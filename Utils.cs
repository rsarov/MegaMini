using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System.Security.Cryptography;
using System.Text;

namespace MegaMini
{
    internal class Utils
    {
        public static byte[] FromBase64(string data)
        {
            var sb = new StringBuilder();
            sb.Append(data);
            sb.Append(string.Empty.PadRight((4 - data.Length % 4) % 4, '='));
            sb.Replace('-', '+');
            sb.Replace('_', '/');
            sb.Replace(",", string.Empty);

            return Convert.FromBase64String(sb.ToString());
        }

        public static byte[] ToBytes(string data)
        {
            return Encoding.UTF8.GetBytes(data);
        }

        public static string StreamToString(Stream stream)
        {
            using var streamReader = new StreamReader(stream, Encoding.UTF8);
            return streamReader.ReadToEnd();
        }

        public static T[] CopySubArray<T>(T[] source, int length, int offset = 0)
        {
            var result = new T[length];
            while (--length >= 0)
            {
                if (source.Length > offset + length)
                {
                    result[length] = source[offset + length];
                }
            }

            return result;
        }

        public static byte[] DecryptAes(byte[] data, byte[] key)
        {
            Aes s_aesCbc = Aes.Create();
            s_aesCbc.Padding = PaddingMode.None;
            s_aesCbc.Mode = CipherMode.CBC;
            byte[] s_defaultIv = new byte[16];
            using ICryptoTransform decryptor = s_aesCbc.CreateDecryptor(key, s_defaultIv);
            return decryptor.TransformFinalBlock(data, 0, data.Length);
        }

        public static byte[] DecryptKey(byte[] data, byte[] key)
        {
            byte[] result = new byte[data.Length];

            for (var idx = 0; idx < data.Length; idx += 16)
            {
                byte[] block = CopySubArray(data, 16, idx);
                byte[] decryptedBlock = DecryptAes(block, key);
                Array.Copy(decryptedBlock, 0, result, idx, 16);
            }

            return result;
        }

        public static void GetPartsFromDecryptedKey(byte[] decryptedKey, out byte[] iv, out byte[] metaMac, out byte[] fileKey)
        {
            // Extract Iv and MetaMac
            iv = new byte[8];
            metaMac = new byte[8];
            Array.Copy(decryptedKey, 16, iv, 0, 8);
            Array.Copy(decryptedKey, 24, metaMac, 0, 8);

            // For files, key is 256 bits long. Compute the key to retrieve 128 AES key
            fileKey = new byte[16];
            for (var idx = 0; idx < 16; idx++)
            {
                fileKey[idx] = (byte)(decryptedKey[idx] ^ decryptedKey[idx + 16]);
            }
        }

        public static string ToUTF8String(byte[] data)
        {
            return Encoding.UTF8.GetString(data);
        }

        public static string? GetName(byte[] attributes, byte[] nodeKey)
        {
            byte[] decryptedAttributes = DecryptAes(attributes, nodeKey);

            // Remove MEGA prefix
            try
            {
                string json = Utils.ToUTF8String(decryptedAttributes)[4..];
                int nullTerminationIndex = json.IndexOf('\0');
                if (nullTerminationIndex != -1)
                {
                    json = json[..nullTerminationIndex];
                    JToken jToken = (JToken)JObject.Parse(json);
                    return jToken.Value<string>("n");
                }

                return string.Empty;
            }
            catch
            {
                throw;
            }
        }

        public static ICryptoTransform CreateAesEncryptor(byte[] key)
        {
            Aes s_aesCbc = Aes.Create();
            s_aesCbc.Padding = PaddingMode.None;
            s_aesCbc.Mode = CipherMode.CBC;
            byte[] s_defaultIv = new byte[16];
            return new CachedCryptoTransform(() => s_aesCbc.CreateEncryptor(key, s_defaultIv), true);
        }

        public static byte[] EncryptAes(byte[] data, ICryptoTransform encryptor)
        {
            return encryptor.TransformFinalBlock(data, 0, data.Length);
        }
    }
}
