using Newtonsoft.Json.Linq;
using System.Collections.Generic;
using System.Globalization;
using System.Net;
using System.Net.Http.Headers;
using System.Text.RegularExpressions;

namespace MegaMini
{
    internal class MegaMini
    {
        private const int bufferSize = 1024 * 64;
        private const int responseTimeout = Timeout.Infinite;
        private const string applicationKey = "axhQiYyQ";
        private const string baseLink = "https://g.api.mega.co.nz/cs";
        private static uint sequenceIndex = (uint)(uint.MaxValue * new Random().NextDouble());
        public static List<MegaFile>? GetNodesFromLink(string link)
        {
            ArgumentNullException.ThrowIfNull(link);

            if (link.StartsWith("https://mega.nz/folder/") == false)
            {
                throw new ArgumentException("Link must be a valid folder share starting with /folder/. Use GetNodeFromLink() for file share", nameof(link));
            }

            GetIdAndKeyFromLink(link, out string? shareId, out byte[]? decryptedKey);

            if (shareId == null || decryptedKey == null)
            {
                return null;
            }

            string url = baseLink + "?n=" + shareId
                + "&id=" + (sequenceIndex++ % uint.MaxValue).ToString(CultureInfo.InvariantCulture).ToString()
                + "&ak=" + applicationKey;
            Stream dataStream = new MemoryStream(Utils.ToBytes("[{\"c\":1,\"r\":1,\"a\":\"f\"}]"));
            Stream requestStream = PostRequest(url, dataStream, "application/json");
            String json = Utils.StreamToString(requestStream);
            List<JToken> allF = [.. JArray.Parse(json).SelectMany(x => x["f"] ?? new JArray())];
            List<MegaFile> megaFiles = [];

            foreach (JToken jToken in allF)
            {
                string? id = jToken.Value<string>("h");
                string? serializedKey = jToken.Value<string>("k");
                int type = jToken.Value<int>("t");
                string? serializedAttributes = jToken.Value<string>("a");

                if (id != null && serializedKey != null && serializedAttributes != null)
                {
                    serializedKey = serializedKey.Split('/')[0];
                    int splitPosition = serializedKey.IndexOf(':');
                    byte[] encryptedKey = Utils.FromBase64(serializedKey[(splitPosition + 1)..]);
                    byte[] fullKey = Utils.DecryptKey(encryptedKey, decryptedKey);

                    if (type == 0)
                    {
                        Utils.GetPartsFromDecryptedKey(fullKey, out var iv, out var metaMac, out var fileKey);
                        string? name = Utils.GetName(Utils.FromBase64(serializedAttributes), fileKey);
                        MegaFile megaObject = new(id, name, shareId, iv, metaMac, fileKey);
                        megaFiles.Add(megaObject);
                    }
                }
            }

            return megaFiles;
        }

        public static Stream? Download(MegaFile megaFile)
        {
            ArgumentNullException.ThrowIfNull(megaFile);

            string url = baseLink + "?n=" + megaFile.SareId
                + "&id=" + (sequenceIndex++ % uint.MaxValue).ToString(CultureInfo.InvariantCulture).ToString()
                + "&ak=" + applicationKey;
            string dataRequest = "[{\"g\":1,\"n\":\"" + megaFile.Id + "\",\"a\":\"g\"}]";
            Stream dataStream = new MemoryStream(Utils.ToBytes(dataRequest));
            Stream requestStream = PostRequest(url, dataStream, "application/json");
            String json = Utils.StreamToString(requestStream);
            string? fileUrl = JArray.Parse(json)[0].Value<string>("g");
            long size = JArray.Parse(json)[0].Value<long>("s");
            HttpClient httpClient = new(new HttpClientHandler { AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate })
            {
                Timeout = TimeSpan.FromMilliseconds(responseTimeout)
            };
            Stream fileStream = httpClient.GetStreamAsync(fileUrl).Result;

            Stream bufferedStream = new BufferedStream(fileStream);
            if (megaFile.Key != null && megaFile.Iv != null && megaFile.MetaMac != null)
            {
                Stream? resultStream = new MegaAesCtrStreamDecrypter(bufferedStream, size, megaFile.Key, megaFile.Iv, megaFile.MetaMac);
                return resultStream;
            }
            else
            {
                return null;
            }
        }

        private static void GetIdAndKeyFromLink(string link, out string? shareId, out byte[]? decryptedKey)
        {
            shareId = null;
            decryptedKey = null;
            Regex regex = new(@"/(?<type>(file|folder))/(?<id>[^#]+)#(?<key>[^$/]+)", RegexOptions.IgnoreCase);
            Match match = regex.Match(link);
            if (match.Success)
            {
                shareId = match.Groups["id"].Value;
                decryptedKey = Utils.FromBase64(match.Groups["key"].Value);

            }
        }

        private static Stream PostRequest(string url, Stream dataStream, string contentType)
        {
            using StreamContent content = new(dataStream, bufferSize);
            content.Headers.ContentType = new MediaTypeHeaderValue(contentType);

            HttpRequestMessage requestMessage = new(HttpMethod.Post, url)
            {
                Content = content
            };

            HttpClient httpClient = new(new HttpClientHandler { AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate })
            {
                Timeout = TimeSpan.FromMilliseconds(responseTimeout)
            };
            HttpResponseMessage response = httpClient.SendAsync(requestMessage, HttpCompletionOption.ResponseHeadersRead).Result;
            response.EnsureSuccessStatusCode();
            return response.Content.ReadAsStreamAsync().Result;
        }
    }

    public struct MegaFile(string? id, string? name, string? shareId, byte[]? iv, byte[]? metaMac, byte[]? key)
    {
        public string? Id = id;
        public string? Name = name;
        public string? SareId = shareId;
        public byte[]? Iv = iv;
        public byte[]? MetaMac = metaMac;
        public byte[]? Key = key;
    }
}
