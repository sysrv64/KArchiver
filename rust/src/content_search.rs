use std::io::{self, Read};

pub const DEFAULT_MAX_BYTES: u64 = 32 * 1024 * 1024;
const BINARY_PROBE: usize = 8192;
const MAX_LINE: usize = 256 * 1024;
const SNIPPET_MAX: usize = 160;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ContentMatch {
    pub name: String,
    pub line: u64,
    pub snippet: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LineMatch {
    pub line: u64,
    pub snippet: String,
}

pub struct Scanner {
    needle: Vec<u8>,
    case_sensitive: bool,
    max_bytes: u64,
    consumed: u64,
    line: u64,
    buf: Vec<u8>,
    start: usize,
    probe_len: usize,
    binary: bool,
    result: Option<LineMatch>,
    done: bool,
}

impl Scanner {
    pub fn new(needle: &str, case_sensitive: bool, max_bytes: u64) -> Option<Self> {
        if needle.is_empty() || max_bytes == 0 {
            return None;
        }
        let needle = if case_sensitive {
            needle.as_bytes().to_vec()
        } else {
            needle.to_ascii_lowercase().into_bytes()
        };
        Some(Self {
            needle,
            case_sensitive,
            max_bytes,
            consumed: 0,
            line: 1,
            buf: Vec::new(),
            start: 0,
            probe_len: 0,
            binary: false,
            result: None,
            done: false,
        })
    }

    pub fn feed(&mut self, data: &[u8]) {
        if self.done || data.is_empty() {
            return;
        }
        if self.probe_len < BINARY_PROBE {
            let take = (BINARY_PROBE - self.probe_len).min(data.len());
            if data[..take].contains(&0) {
                self.binary = true;
                self.done = true;
                return;
            }
            self.probe_len += take;
        }
        let remaining = self.max_bytes.saturating_sub(self.consumed);
        if remaining == 0 {
            self.done = true;
            return;
        }
        let take = (data.len() as u64).min(remaining) as usize;
        self.consumed += take as u64;
        self.buf.extend_from_slice(&data[..take]);
        self.drain_lines(false);
        if self.consumed >= self.max_bytes {
            self.drain_lines(true);
        }
        if self.result.is_some() {
            self.done = true;
        }
    }

    pub fn is_done(&self) -> bool {
        self.done
    }

    pub fn finish(&mut self) -> Option<LineMatch> {
        if self.result.is_none() && !self.binary && !self.done {
            self.drain_lines(true);
        }
        self.result.take()
    }

    fn drain_lines(&mut self, at_end: bool) {
        while let Some(rel) = self.buf[self.start..].iter().position(|b| *b == b'\n') {
            let end = self.start + rel;
            let line_num = self.line;
            let matched = self.match_slice(self.start, end, line_num);
            self.start = end + 1;
            if matched {
                return;
            }
            self.line += 1;
        }
        if at_end {
            if self.start < self.buf.len() {
                let line_num = self.line;
                let end = self.buf.len();
                self.match_slice(self.start, end, line_num);
            }
            self.buf.clear();
            self.start = 0;
            return;
        }
        if self.buf.len() - self.start > MAX_LINE {
            let line_num = self.line;
            let end = self.buf.len();
            let matched = self.match_slice(self.start, end, line_num);
            if matched {
                return;
            }
            let keep = self.needle.len().saturating_sub(1);
            if keep == 0 {
                self.buf.clear();
            } else {
                let keep_from = self.buf.len().saturating_sub(keep);
                self.buf.copy_within(keep_from.., 0);
                self.buf.truncate(self.buf.len() - keep_from);
            }
            self.start = 0;
            return;
        }
        if self.start > 0 {
            self.buf.drain(..self.start);
            self.start = 0;
        }
    }

    fn match_slice(&mut self, from: usize, to: usize, line_num: u64) -> bool {
        let line = &self.buf[from..to];
        let at = if self.case_sensitive {
            find_subslice(line, &self.needle)
        } else {
            let hay = line.to_ascii_lowercase();
            find_subslice(&hay, &self.needle)
        };
        match at {
            Some(at) => {
                let snippet = snippet(&self.buf[from..to], at);
                self.result = Some(LineMatch {
                    line: line_num,
                    snippet,
                });
                true
            }
            None => false,
        }
    }
}

pub fn find_subslice(hay: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || hay.len() < needle.len() {
        return None;
    }
    let limit = hay.len() - needle.len();
    for i in 0..=limit {
        if &hay[i..i + needle.len()] == needle {
            return Some(i);
        }
    }
    None
}

pub fn snippet(line: &[u8], at: usize) -> String {
    let mut start = at.saturating_sub(SNIPPET_MAX / 3);
    let mut end = (start + SNIPPET_MAX).min(line.len());
    if end - start < SNIPPET_MAX {
        start = end.saturating_sub(SNIPPET_MAX);
        end = (start + SNIPPET_MAX).min(line.len());
    }
    let prefix = if start > 0 { "…" } else { "" };
    let suffix = if end < line.len() { "…" } else { "" };
    let slice = String::from_utf8_lossy(&line[start..end]);
    format!("{prefix}{}{suffix}", slice.trim())
}

pub fn scan_reader<R: Read>(
    reader: &mut R,
    needle: &str,
    case_sensitive: bool,
    max_bytes: u64,
) -> io::Result<Option<LineMatch>> {
    let Some(mut scanner) = Scanner::new(needle, case_sensitive, max_bytes) else {
        return Ok(None);
    };
    let mut buf = vec![0u8; 64 * 1024];
    while !scanner.is_done() {
        let n = reader.read(&mut buf)?;
        if n == 0 {
            break;
        }
        scanner.feed(&buf[..n]);
    }
    Ok(scanner.finish())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scan_text(text: &str, needle: &str, case_sensitive: bool) -> Option<LineMatch> {
        let mut cursor = io::Cursor::new(text.as_bytes().to_vec());
        scan_reader(&mut cursor, needle, case_sensitive, DEFAULT_MAX_BYTES).unwrap()
    }

    #[test]
    fn finds_needle_on_first_line() {
        let m = scan_text("hello world\nsecond", "world", false).unwrap();
        assert_eq!(m.line, 1);
        assert_eq!(m.snippet, "hello world");
    }

    #[test]
    fn reports_one_based_line_number() {
        let m = scan_text("alpha\nbeta\ngamma needle", "needle", false).unwrap();
        assert_eq!(m.line, 3);
    }

    #[test]
    fn case_insensitive_by_default() {
        assert!(scan_text("Hello World", "hello", false).is_some());
        assert!(scan_text("Hello World", "HELLO", true).is_none());
        assert!(scan_text("Hello World", "Hello", true).is_some());
    }

    #[test]
    fn binary_is_skipped() {
        let mut data = vec![b'a', 0, b'b'];
        data.extend_from_slice(b"needle");
        let mut cursor = io::Cursor::new(data);
        let found = scan_reader(&mut cursor, "needle", false, DEFAULT_MAX_BYTES).unwrap();
        assert!(found.is_none());
    }

    #[test]
    fn size_cap_stops_scanning() {
        let text = format!("{}needle", "x".repeat(4096));
        let mut cursor = io::Cursor::new(text.into_bytes());
        let found = scan_reader(&mut cursor, "needle", false, 64).unwrap();
        assert!(found.is_none());
    }

    #[test]
    fn matches_across_small_reads() {
        struct Tiny<R> {
            inner: R,
        }
        impl<R: Read> Read for Tiny<R> {
            fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
                if buf.is_empty() {
                    return Ok(0);
                }
                self.inner.read(&mut buf[..1])
            }
        }
        let mut reader = Tiny {
            inner: io::Cursor::new(b"the complete needle is here".to_vec()),
        };
        let found = scan_reader(&mut reader, "complete needle", false, DEFAULT_MAX_BYTES).unwrap();
        assert!(found.is_some());
        assert_eq!(found.unwrap().line, 1);
    }

    #[test]
    fn snippet_is_trimmed_and_capped() {
        let long = format!("   {}   ", "a".repeat(400));
        let m = scan_text(&long, "aaa", false).unwrap();
        assert!(m.snippet.contains("aaa"));
        assert!(m.snippet.len() <= SNIPPET_MAX + 2);
        assert!(!m.snippet.starts_with(' '));
        assert!(!m.snippet.ends_with(' '));
    }

    #[test]
    fn empty_needle_is_ignored() {
        assert!(scan_text("anything", "", false).is_none());
    }

    #[test]
    fn scanner_detects_binary_flag() {
        let mut scanner = Scanner::new("x", false, DEFAULT_MAX_BYTES).unwrap();
        scanner.feed(&[0u8, 1, 2]);
        assert!(scanner.is_done());
        assert!(scanner.finish().is_none());
    }

    #[test]
    fn newline_dense_input_still_matches() {
        let mut data = vec![b'\n'; 200_000];
        data.extend_from_slice(b"needle");
        let mut scanner = Scanner::new("needle", false, u64::MAX).unwrap();
        scanner.feed(&data);
        let m = scanner.finish().unwrap();
        assert_eq!(m.line, 200_001);
    }

    #[test]
    fn long_single_line_without_newline_is_capped() {
        let data = vec![b'x'; MAX_LINE * 3];
        let mut cursor = io::Cursor::new(data);
        let found = scan_reader(&mut cursor, "needle", false, DEFAULT_MAX_BYTES).unwrap();
        assert!(found.is_none());
    }
}
