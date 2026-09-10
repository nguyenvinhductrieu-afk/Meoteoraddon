#!/usr/bin/env python3
"""
obfuscate.py — Inject junk code into Java source files before build.

Usage:
    python obfuscate.py --apply     # Inject junk (backs up originals to .orig)
    python obfuscate.py --restore   # Restore originals from .orig backups
"""

import os, re, random, shutil, sys

# ── Config ─────────────────────────────────────────────────────────────────────
ROOT     = os.path.dirname(os.path.abspath(__file__))
SRC_DIR  = os.path.join(ROOT, "src", "main", "java", "com", "example", "addon")
BAK_EXT  = ".orig"

FIELD_RANGE  = (14, 22)   # number of fake fields injected per class
METHOD_RANGE = (9, 15)    # number of fake methods injected per class

random.seed()  # Different junk every build

# ── Tiny helpers ───────────────────────────────────────────────────────────────
def rh(n=4):
    return ''.join(random.choices('0123456789abcdef', k=n))

def ri(lo, hi):
    return random.randint(lo, hi)

def rname(pfx="__jf"):
    return f"{pfx}_{rh(3)}_{rh(3)}"

# ── Junk field generators ──────────────────────────────────────────────────────
def _f_int():
    name = rname("__fi")
    v1   = ri(0, 0xFFFFFFFF)
    v2   = ri(1, 0xFFFF)
    return f'    @SuppressWarnings("all") private static final int {name} = 0x{v1:08X} ^ 0x{v2:04X};'

def _f_long():
    name = rname("__fl")
    v    = ri(0, 0xFFFFFFFFFFFF)
    m    = ri(1, 0xFFFFFFFF)
    return f'    @SuppressWarnings("all") private static final long {name} = 0x{v:012X}L * 0x{m:08X}L;'

def _f_bool():
    name = rname("__fb")
    v    = rh(4)
    return f'    @SuppressWarnings("all") private static final boolean {name} = (0x{v} & 1) != 0;'

def _f_byte():
    name = rname("__fy")
    v    = ri(0, 0xFF)
    return f'    @SuppressWarnings("all") private static final byte {name} = (byte)0x{v:02X};'

def _f_str():
    name    = rname("__fs")
    chars   = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    content = ''.join(random.choice(chars) for _ in range(ri(6, 14)))
    return f'    @SuppressWarnings("all") private static final String {name} = "{content}";'

def _f_short():
    name = rname("__fh")
    v    = ri(0, 0x7FFF)
    xor  = ri(0, 0xFF)
    return f'    @SuppressWarnings("all") private static final short {name} = (short)(0x{v:04X} ^ 0x{xor:02X});'

_FIELD_GENS = [_f_int, _f_long, _f_bool, _f_byte, _f_str, _f_short]

def gen_fields(count):
    return [random.choice(_FIELD_GENS)() for _ in range(count)]

# ── Junk method generators ─────────────────────────────────────────────────────
def _m_int_rotate():
    name  = rname("__jm")
    rl    = ri(1, 15); rr = 32 - rl
    iters = ri(4, 12)
    seed  = ri(1, 0xFFFF)
    xor   = ri(1, 0xFFFF)
    body  = (
        f"        int _r = _x ^ (_y * 0x{seed:04X});\n"
        f"        for (int _i = 0; _i < {iters}; _i++) {{\n"
        f"            _r = (_r << {rl}) | (_r >>> {rr});\n"
        f"            _r ^= _i ^ 0x{xor:04X};\n"
        f"        }}\n"
        f"        return _r & 0x7FFFFFFF;"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static int {name}(int _x, int _y) {{\n{body}\n    }}")

def _m_long_hash():
    name = rname("__jm")
    m1   = ri(0x100000001, 0x7FFFFFFFFFFFFFFF)
    m2   = ri(0x100000001, 0x7FFFFFFFFFFFFFFF)
    sh1  = ri(17, 32); sh2 = ri(17, 32)
    body = (
        f"        long _h = _n ^ (_n >>> {sh1});\n"
        f"        _h *= 0x{m1:016X}L;\n"
        f"        _h ^= _h >>> {sh2};\n"
        f"        _h *= 0x{m2:016X}L;\n"
        f"        _h ^= _h >>> {sh1};\n"
        f"        return _h;"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static long {name}(long _n) {{\n{body}\n    }}")

def _m_string_xor():
    name = rname("__jm")
    xk   = ri(1, 0x7E)
    body = (
        f"        char[] _c = _s.toCharArray();\n"
        f"        for (int _i = 0; _i < _c.length; _i++) {{\n"
        f"            _c[_i] = (char)(_c[_i] ^ (0x{xk:02X} + (_i & 0x0F)));\n"
        f"        }}\n"
        f"        return new String(_c);"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static String {name}(String _s) {{\n{body}\n    }}")

def _m_bool_check():
    name = rname("__jm")
    v    = ri(1, 0xFFFF)
    thr  = ri(100, 210)
    xk   = ri(1, 0xFFFF)
    body = (
        f"        int _t = (_x * 0x{v:04X}) ^ (_y + 0x{xk:04X});\n"
        f"        return (_t & 0xFF) > {thr};"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static boolean {name}(int _x, int _y) {{\n{body}\n    }}")

def _m_bytes_scramble():
    name = rname("__jm")
    seed = ri(1, 0xFF)
    body = (
        f"        byte[] _o = new byte[_b.length];\n"
        f"        for (int _i = 0; _i < _b.length; _i++) {{\n"
        f"            _o[_i] = (byte)(_b[_i] ^ (byte)(0x{seed:02X} + _i));\n"
        f"        }}\n"
        f"        return _o;"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static byte[] {name}(byte[] _b) {{\n{body}\n    }}")

def _m_nested_loops():
    name  = rname("__jm")
    rl    = ri(1, 31); rr = 64 - rl   # 64-bit shift for long
    outer = ri(3, 7); inner = ri(2, 5)
    xk    = ri(1, 0xFFFF)
    body  = (
        f"        long _acc = (_seed & 0xFFFFFFFFL) * 0x{xk:04X}L;\n"
        f"        for (int _i = 0; _i < {outer}; _i++) {{\n"
        f"            for (int _j = 0; _j < {inner}; _j++) {{\n"
        f"                _acc = (_acc << {rl}) | (_acc >>> {rr});\n"
        f"                _acc ^= (long)(_i ^ _j) + 0x{ri(1, 0xFFFF):04X}L;\n"
        f"            }}\n"
        f"        }}\n"
        f"        return (int)(_acc & 0x7FFFFFFFL);"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static int {name}(int _seed) {{\n{body}\n    }}")

def _m_array_fill():
    name = rname("__jm")
    seed = ri(1, 0xFFFF)
    body = (
        f"        int[] _arr = new int[_len & 0xFF];\n"
        f"        for (int _i = 0; _i < _arr.length; _i++) {{\n"
        f"            _arr[_i] = (_i * 0x{seed:04X}) ^ (_val >>> _i % 32);\n"
        f"        }}\n"
        f"        return _arr.length;"
    )
    return (f'    @SuppressWarnings("all")\n'
            f"    private static int {name}(int _len, int _val) {{\n{body}\n    }}")

_METHOD_GENS = [_m_int_rotate, _m_long_hash, _m_string_xor,
                _m_bool_check, _m_bytes_scramble, _m_nested_loops, _m_array_fill]

def gen_methods(count):
    # Rotate through all types for maximum variety
    gens = (_METHOD_GENS * ((count // len(_METHOD_GENS)) + 2))[:count]
    random.shuffle(gens)
    return [g() for g in gens]

# ── Class body locator (handles strings & comments) ────────────────────────────
def _skip_string(code, i):
    i += 1
    while i < len(code):
        if code[i] == '\\': i += 2
        elif code[i] == '"': return i + 1
        else: i += 1
    return i

def _skip_char(code, i):
    i += 1
    while i < len(code):
        if code[i] == '\\': i += 2
        elif code[i] == "'": return i + 1
        else: i += 1
    return i

def find_class_bounds(code):
    """
    Find the outer class opening '{' and closing '}' positions.
    Returns (open_pos, close_pos) or (None, None) if no public class found.
    Correctly skips string literals, char literals, and comments.
    """
    m = re.search(r'\bpublic\s+(?:(?:final|abstract)\s+)*class\b', code)
    if not m:
        return None, None

    depth    = 0
    open_pos = None
    i        = m.start()

    while i < len(code):
        c = code[i]
        # Skip string literals
        if c == '"':
            i = _skip_string(code, i); continue
        # Skip char literals
        if c == "'":
            i = _skip_char(code, i); continue
        # Skip single-line comment
        if c == '/' and i + 1 < len(code) and code[i+1] == '/':
            while i < len(code) and code[i] != '\n': i += 1
            continue
        # Skip block comment
        if c == '/' and i + 1 < len(code) and code[i+1] == '*':
            i += 2
            while i + 1 < len(code) and not (code[i] == '*' and code[i+1] == '/'): i += 1
            i += 2; continue

        if c == '{':
            depth += 1
            if depth == 1: open_pos = i
        elif c == '}':
            depth -= 1
            if depth == 0 and open_pos is not None:
                return open_pos, i
        i += 1

    return open_pos, None

# ── Core injection ─────────────────────────────────────────────────────────────
def inject_junk(code):
    open_pos, close_pos = find_class_bounds(code)
    if open_pos is None or close_pos is None:
        return code   # interfaces, enums-only, etc. → skip

    fields  = gen_fields(ri(*FIELD_RANGE))
    methods = gen_methods(ri(*METHOD_RANGE))

    fields_block  = "\n    // ========== junk-fields ==========\n"  + "\n".join(fields)  + "\n    // =================================\n"
    methods_block = "\n\n    // ========== junk-methods ==========\n" + "\n\n".join(methods) + "\n    // ==================================\n"

    # Insert fields right after the opening '{', methods right before closing '}'
    return (
        code[:open_pos + 1] +
        fields_block +
        code[open_pos + 1 : close_pos] +
        methods_block +
        code[close_pos:]
    )

# ── File operations ────────────────────────────────────────────────────────────
def apply_obfuscation():
    n = 0
    for root, _, files in os.walk(SRC_DIR):
        for fname in sorted(files):
            if not fname.endswith('.java'): continue
            path = os.path.join(root, fname)
            bak  = path + BAK_EXT
            if os.path.exists(bak):
                print(f"  [skip] {fname}  (backup already exists)")
                continue
            with open(path, 'r', encoding='utf-8') as f: src = f.read()
            result = inject_junk(src)
            if result == src:
                print(f"  [skip] {fname}  (no injectable class)")
                continue
            shutil.copy2(path, bak)
            with open(path, 'w', encoding='utf-8') as f: f.write(result)
            print(f"  [OK]   {fname}")
            n += 1
    print(f"\n✓ {n} file(s) obfuscated.")

def restore_originals():
    n = 0
    for root, _, files in os.walk(SRC_DIR):
        for fname in sorted(files):
            if not fname.endswith(BAK_EXT): continue
            bak  = os.path.join(root, fname)
            orig = bak[:-len(BAK_EXT)]
            shutil.copy2(bak, orig)
            os.remove(bak)
            print(f"  [OK]   {os.path.basename(orig)}")
            n += 1
    print(f"\n✓ {n} file(s) restored.")

# ── Entry ──────────────────────────────────────────────────────────────────────
if __name__ == '__main__':
    if '--apply' in sys.argv:
        print("════════════════════════════════════════")
        print("  Junk Code Injector — APPLY MODE")
        print("════════════════════════════════════════")
        apply_obfuscation()
    elif '--restore' in sys.argv:
        print("════════════════════════════════════════")
        print("  Junk Code Injector — RESTORE MODE")
        print("════════════════════════════════════════")
        restore_originals()
    else:
        print("Usage: python obfuscate.py [--apply | --restore]")
        sys.exit(1)
