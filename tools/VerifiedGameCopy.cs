using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Threading.Tasks;

namespace Vbrasfoot.Validation
{
    public sealed class CopiedFile
    {
        public string Path { get; set; }
        public string SHA256 { get; set; }
        public long Bytes { get; set; }
    }

    // Only copies into a new, caller-owned destination. No game execution or restoration.
    public static class VerifiedGameCopy
    {
        public static string[] Verify(string root, CopiedFile[] baseline)
        {
            var differences = new ConcurrentBag<string>();
            Parallel.ForEach(baseline, new ParallelOptions { MaxDegreeOfParallelism = 4 }, record =>
            {
                string path = System.IO.Path.Combine(root, record.Path);
                try
                {
                    Plain(path);
                    if (Digest(path) != record.SHA256) differences.Add(record.Path);
                }
                catch (IOException) { differences.Add(record.Path); }
            });
            return differences.OrderBy(p => p, StringComparer.Ordinal).ToArray();
        }

        private static void Plain(string path)
        {
            if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Link/junction refused: " + path);
        }

        private static string Digest(string path)
        {
            using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read))
            using (var sha = SHA256.Create())
                return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
        }

        public static CopiedFile[] Copy(string source, string destination)
        {
            source = System.IO.Path.GetFullPath(source).TrimEnd(System.IO.Path.DirectorySeparatorChar);
            destination = System.IO.Path.GetFullPath(destination);
            if (Directory.Exists(destination) || File.Exists(destination)) throw new IOException("Destination must be new");
            if (destination.StartsWith(source + System.IO.Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
                throw new IOException("Destination overlaps source");
            for (string p = source; p != null; p = System.IO.Path.GetDirectoryName(p)) Plain(p);
            for (string p = System.IO.Path.GetDirectoryName(destination); p != null; p = System.IO.Path.GetDirectoryName(p))
                if (Directory.Exists(p)) Plain(p);
            var files = new List<string>();
            var directories = new List<string>();
            var pending = new Stack<string>();
            pending.Push(source);
            while (pending.Count > 0)
            {
                string directory = pending.Pop();
                Plain(directory);
                directories.Add(directory);
                foreach (string entry in Directory.GetFileSystemEntries(directory))
                {
                    Plain(entry);
                    if (Directory.Exists(entry)) pending.Push(entry); else files.Add(entry);
                }
            }
            foreach (string directory in directories)
                Directory.CreateDirectory(System.IO.Path.Combine(destination, directory.Substring(source.Length).TrimStart(System.IO.Path.DirectorySeparatorChar)));
            var result = new ConcurrentBag<CopiedFile>();
            // Bounded concurrency amortizes Windows small-file overhead, not an unbounded task per file.
            Parallel.ForEach(files, new ParallelOptions { MaxDegreeOfParallelism = 4 }, file =>
            {
                Plain(file);
                string relative = file.Substring(source.Length + 1);
                string target = System.IO.Path.Combine(destination, relative);
                string before = Digest(file);
                File.Copy(file, target, false);
                if (Digest(target) != before || Digest(file) != before)
                    throw new IOException("Source changed during copy: " + relative);
                result.Add(new CopiedFile { Path = relative, SHA256 = before, Bytes = new FileInfo(target).Length });
            });
            return result.OrderBy(r => r.Path, StringComparer.Ordinal).ToArray();
        }
    }
}
