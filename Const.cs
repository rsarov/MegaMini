namespace MegaMini
{
    internal class Const
    {
        public const int bufferSize = 1024 * 64;        
        public const int responseTimeout = Timeout.Infinite;
        public const string applicationKey = "axhQiYyQ";
        public const string baseLink = "https://g.api.mega.co.nz/cs";
        public static uint sequenceIndex = (uint)(uint.MaxValue * new Random().NextDouble());
    }
}
