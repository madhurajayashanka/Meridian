#!/usr/bin/env python3
"""Generate an RSA-2048 keypair and inject it into .env.

Handles both forms of existing value:
  JWT_SECRET_KEY="<multi-line PEM>"   — replaces the whole quoted block
  JWT_SECRET_KEY=placeholder           — replaces a single-line placeholder
"""
import os
import re
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENV_FILE = os.path.join(ROOT, ".env")


def generate_keypair():
    with tempfile.TemporaryDirectory() as tmpdir:
        priv_path = os.path.join(tmpdir, "private.pem")
        pub_path = os.path.join(tmpdir, "public.pem")
        subprocess.run(
            ["openssl", "genrsa", "-out", priv_path, "2048"],
            capture_output=True,
            check=True,
        )
        subprocess.run(
            ["openssl", "rsa", "-in", priv_path, "-pubout", "-out", pub_path],
            capture_output=True,
            check=True,
        )
        return open(priv_path).read().strip(), open(pub_path).read().strip()


def replace_env_key(content, var, value):
    """Replace VAR="..." (quoted, possibly multi-line) or VAR=plain (single line)."""
    quoted_pattern = var + r'="[^"]*"'
    if re.search(quoted_pattern, content):
        return re.sub(quoted_pattern, f'{var}="{value}"', content)
    return re.sub(
        r"^" + var + r"=.*$",
        f'{var}="{value}"',
        content,
        flags=re.MULTILINE,
    )


def main():
    if not os.path.isfile(ENV_FILE):
        print(f"ERROR: {ENV_FILE} not found. Run 'make dev-setup' first.")
        raise SystemExit(1)

    print("Generating RSA-2048 keypair...")
    priv, pub = generate_keypair()

    with open(ENV_FILE) as f:
        content = f.read()

    content = replace_env_key(content, "JWT_SECRET_KEY", priv)
    content = replace_env_key(content, "JWT_PUBLIC_KEY", pub)

    with open(ENV_FILE, "w") as f:
        f.write(content)

    print("  ✓ JWT_SECRET_KEY written (RSA-2048 private key)")
    print("  ✓ JWT_PUBLIC_KEY  written (RSA-2048 public key)")


if __name__ == "__main__":
    main()
