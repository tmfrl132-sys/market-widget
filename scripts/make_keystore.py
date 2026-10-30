"""APK 서명 키(PKCS12)를 한 번 만든다.

사용: uv run --with cryptography python scripts/make_keystore.py <출력 폴더>
출력 폴더에 market.p12, market.p12.b64, password.txt를 쓴다. 비밀 값은 화면에 출력하지 않는다.
"""

import base64
import datetime
import secrets
import sys
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

ALIAS = b"market"


def main(out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(24)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "market-widget")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=365 * 30))
        .sign(key, hashes.SHA256())
    )
    p12 = pkcs12.serialize_key_and_certificates(
        ALIAS, key, cert, None, serialization.BestAvailableEncryption(password.encode())
    )
    (out_dir / "market.p12").write_bytes(p12)
    (out_dir / "market.p12.b64").write_text(base64.b64encode(p12).decode(), newline="")
    (out_dir / "password.txt").write_text(password, newline="")
    print(f"키 생성 완료: {out_dir}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
