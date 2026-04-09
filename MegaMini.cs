using Newtonsoft.Json.Linq;
using System.Globalization;
using System.Net;

namespace MegaMini
{
    internal class MegaMini
    {
        public static List<MegaFile>? GetNodesFromLink(string link)
        {
            ArgumentNullException.ThrowIfNull(link);

            if (link.StartsWith("https://mega.nz/folder/") == false)
            {
                throw new ArgumentException("Link must be a valid folder share starting with /folder/. Use GetNodeFromLink() for file share", nameof(link));
            }

            Utils.GetIdAndKeyFromLink(link, out string? shareId, out byte[]? decryptedKey);

            if (shareId == null || decryptedKey == null)
            {
                return null;
            }

            string url = Const.baseLink + "?n=" + shareId
                + "&id=" + (Const.sequenceIndex++ % uint.MaxValue).ToString(CultureInfo.InvariantCulture).ToString()
                + "&ak=" + Const.applicationKey;
            Stream dataStream = new MemoryStream(Utils.ToBytes("[{\"c\":1,\"r\":1,\"a\":\"f\"}]"));
            Stream requestStream = Utils.PostRequest(url, dataStream, "application/json");
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

            string url = Const.baseLink + "?n=" + megaFile.SareId
                + "&id=" + (Const.sequenceIndex++ % uint.MaxValue).ToString(CultureInfo.InvariantCulture).ToString()
                + "&ak=" + Const.applicationKey;
            string dataRequest = "[{\"g\":1,\"n\":\"" + megaFile.Id + "\",\"a\":\"g\"}]";
            Stream dataStream = new MemoryStream(Utils.ToBytes(dataRequest));
            Stream requestStream = Utils.PostRequest(url, dataStream, "application/json");
            String json = Utils.StreamToString(requestStream);
            string? fileUrl = JArray.Parse(json)[0].Value<string>("g");
            long size = JArray.Parse(json)[0].Value<long>("s");
            HttpClient httpClient = new(new HttpClientHandler { AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate })
            {
                Timeout = TimeSpan.FromMilliseconds(Const.responseTimeout)
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
    }
}
