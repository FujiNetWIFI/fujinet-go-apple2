#!/usr/bin/env python3
"""Strip Apple-copyrighted content from the staged FujiNet media images.

The upstream fujinet-firmware Apple II boot disks carry two things a
distributable Android build must not ship (verifyNoEmbeddedRoms catches
both):

- ``A3A2EMU`` on ``autorun.po`` / ``mount-and-boot.po`` -- an Apple II ROM
  image (Applesoft + monitor + video ROM) used to boot CONFIG on a real
  Apple /// in Apple II emulation mode. Inside this app the disk boots under
  AppleWin, which never reads it, so the file is deleted from the ProDOS
  volume and its blocks zeroed.
- DOS 3.3 boot tracks on ``blank.do`` (an INITed blank). Tracks 0-2 are
  zeroed, turning it into a standard non-bootable data disk (VTOC/catalog on
  track $11 are untouched).

Idempotent: already-sanitized images are left alone. Exits non-zero on a
parse failure so the build stops rather than shipping something unknown.

usage: sanitize-apple-media.py <fujinet-data-dir>
"""

import sys
from pathlib import Path

BLOCK = 512
ENTRY_LEN = 0x27
ENTRIES_PER_BLOCK = 13


def read_block(data, n):
    return data[n * BLOCK:(n + 1) * BLOCK]


def file_blocks(data, storage_type, key_block, eof):
    """Data + index blocks of a seedling/sapling/tree file, key included."""
    blocks = [key_block]
    if storage_type == 1:  # seedling: key block is the data
        return blocks
    if storage_type == 2:  # sapling: key is an index block
        idx = read_block(data, key_block)
        for i in range(256):
            b = idx[i] | (idx[256 + i] << 8)
            if b:
                blocks.append(b)
        return blocks
    if storage_type == 3:  # tree: key is a master index block
        master = read_block(data, key_block)
        for i in range(128):
            ib = master[i] | (master[256 + i] << 8)
            if not ib:
                continue
            blocks.append(ib)
            idx = read_block(data, ib)
            for j in range(256):
                b = idx[j] | (idx[256 + j] << 8)
                if b:
                    blocks.append(b)
        return blocks
    raise ValueError(f"unsupported storage type {storage_type}")


def remove_prodos_file(path, victim):
    data = bytearray(path.read_bytes())
    # Volume directory spans blocks 2..5 on a 140K floppy.
    for blk in range(2, 6):
        base = blk * BLOCK
        for slot in range(ENTRIES_PER_BLOCK):
            off = base + 4 + slot * ENTRY_LEN
            entry = data[off:off + ENTRY_LEN]
            if not entry or entry[0] == 0:
                continue
            name_len = entry[0] & 0x0F
            storage = entry[0] >> 4
            name = entry[1:1 + name_len].decode("ascii", "replace")
            if name != victim or storage == 0xF:
                continue
            key = entry[0x11] | (entry[0x12] << 8)
            eof = int.from_bytes(entry[0x15:0x18], "little")
            for b in file_blocks(data, storage, key, eof):
                data[b * BLOCK:(b + 1) * BLOCK] = bytes(BLOCK)
            data[off:off + ENTRY_LEN] = bytes(ENTRY_LEN)
            # Volume header (block 2, first entry): file_count at +0x21.
            hdr = 2 * BLOCK + 4
            count = data[hdr + 0x21] | (data[hdr + 0x22] << 8)
            if count:
                count -= 1
                data[hdr + 0x21] = count & 0xFF
                data[hdr + 0x22] = (count >> 8) & 0xFF
            path.write_bytes(bytes(data))
            return True
    return False


def blank_dos_tracks(path):
    """Zero tracks 0-2 of a 16-sector .do image (the DOS 3.3 image area)."""
    data = bytearray(path.read_bytes())
    span = 3 * 16 * 256
    if not any(data[:span]):
        return False
    data[:span] = bytes(span)
    path.write_bytes(bytes(data))
    return True


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root = Path(sys.argv[1])
    if not root.is_dir():
        # Nothing staged yet -- the staging task orders before this one, so
        # an absent tree just means there is nothing to sanitize.
        print(f"sanitize-apple-media: {root} not found; nothing to do")
        return 0

    for name in ("autorun.po", "mount-and-boot.po"):
        p = root / name
        if not p.is_file():
            continue
        if remove_prodos_file(p, "A3A2EMU"):
            print(f"sanitize-apple-media: removed A3A2EMU from {name}")
        else:
            print(f"sanitize-apple-media: {name} already clean")

    p = root / "blank.do"
    if p.is_file():
        if blank_dos_tracks(p):
            print("sanitize-apple-media: zeroed DOS 3.3 tracks on blank.do")
        else:
            print("sanitize-apple-media: blank.do already clean")

    return 0


if __name__ == "__main__":
    sys.exit(main())
