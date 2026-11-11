#!/usr/bin/env python3
"""下载锁定依赖：分块续传，最终校验；自动使用系统已有代理，不修改系统设置。"""
import concurrent.futures
import hashlib
import json
import re
import shutil
import time
import urllib.request
from pathlib import Path


def digest(path, algorithm="sha256"):
    h = hashlib.new(algorithm)
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def download(url, destination, checksum, algorithm="sha256", workers=6):
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and digest(destination, algorithm) == checksum:
        return destination
    request = urllib.request.Request(url, headers={"Range": "bytes=0-0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        match = re.fullmatch(r"bytes 0-0/(\d+)", response.headers.get("Content-Range", ""))
        total = int(match[1]) if match else None
    if total is None:
        part = destination.with_suffix(destination.suffix + ".part")
        with urllib.request.urlopen(url, timeout=90) as source, part.open("wb") as output:
            shutil.copyfileobj(source, output)
        if digest(part, algorithm) != checksum:
            raise ValueError(f"Checksum mismatch: {destination}")
        part.replace(destination)
        return destination

    chunks = destination.parent / (destination.name + ".chunks")
    chunks.mkdir(exist_ok=True)
    metadata = chunks / "manifest.json"
    if metadata.exists():
        saved = json.loads(metadata.read_text())
        if saved["url"] != url or saved["total"] != total:
            raise ValueError(f"Source changed: {destination}")
        prefix = saved["prefix"]
    else:
        prefix = destination.stat().st_size if destination.exists() else 0
        if prefix >= total:
            raise ValueError(f"Existing complete file has wrong checksum: {destination}")
        if prefix:
            destination.replace(chunks / "prefix")
        metadata.write_text(json.dumps(dict(url=url, total=total, prefix=prefix)))

    def fetch(bounds):
        start, end = bounds
        target = chunks / str(start)
        if target.exists() and target.stat().st_size == end - start + 1:
            return target
        for attempt in range(4):
            try:
                completed = target.stat().st_size if target.exists() else 0
                if completed > end - start + 1:
                    raise ValueError("Invalid cached range")
                offset = start + completed
                request = urllib.request.Request(url, headers={"Range": f"bytes={offset}-{end}"})
                began = time.monotonic()
                with urllib.request.urlopen(request, timeout=30) as source:
                    if source.headers.get("Content-Range") != f"bytes {offset}-{end}/{total}":
                        raise ValueError("Server returned the wrong byte range")
                    with target.open("ab") as output:
                        while True:
                            block = source.read(64 * 1024)
                            if not block:
                                break
                            output.write(block)
                            if time.monotonic() - began > 120:
                                raise TimeoutError("Slow transfer; resume this range on a fresh connection")
                if target.stat().st_size != end - start + 1:
                    raise IOError("Incomplete range")
                return target
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(1 + attempt)

    size = 4 * 1024 * 1024
    ranges = [(start, min(total - 1, start + size - 1)) for start in range(prefix, total, size)]
    print(f"Downloading {destination.name}: {total:,} bytes, resume at {prefix:,}", flush=True)
    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        list(pool.map(fetch, ranges))
    merged = destination.with_suffix(destination.suffix + ".assembling")
    with merged.open("wb") as output:
        sources = ([chunks / "prefix"] if prefix else []) + [chunks / str(start) for start, _ in ranges]
        for path in sources:
            with path.open("rb") as source:
                shutil.copyfileobj(source, output)
    if digest(merged, algorithm) != checksum:
        raise ValueError(f"Checksum mismatch: {destination}; retained chunks for diagnosis")
    merged.replace(destination)
    shutil.rmtree(chunks)
    print(f"Verified {destination.name}", flush=True)
    return destination


if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("url")
    p.add_argument("destination")
    p.add_argument("checksum")
    p.add_argument("--algorithm", default="sha256")
    args = p.parse_args()
    download(args.url, args.destination, args.checksum, args.algorithm)
