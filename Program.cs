using System.Net;

namespace MegaMini
{
    internal class Program
    {
        static void Main()
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            const string link = "https://mega.nz/folder/v2YShZDS#c5yJYxDStBVYUQmnLjc6Xg";
            List<MegaFile>? megaFiles = MegaMini.GetNodesFromLink(link);
            if (megaFiles != null)
            {
                Stream? stream = MegaMini.Download(megaFiles[0]);
                if (stream != null)
                {
                    FileStream fs = new(0 + ".zip", FileMode.Create, FileAccess.Write);
                    stream.CopyTo(fs);
                }
            }
        }
    }
}
