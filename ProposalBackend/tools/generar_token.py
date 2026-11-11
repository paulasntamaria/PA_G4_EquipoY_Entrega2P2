#!/usr/bin/env python3
"""
Genera un JWT HS256 de desarrollo para probar la API en Postman.
No usa librerias externas: solo la libreria estandar de Python.

Uso:  python3 tools/generar_token.py
"""

import base64
import hashlib
import hmac
import json
import time

# Debe coincidir con ETER_JWT_SECRET de infrastructure/.env
SECRET = b"eter-jwt-secret-development-only-CHANGE-IN-PRODUCTION-9f4a8c7e"

EMAIL = "79123456@eter.edu.co"   # sale en el claim sub: destinatario de la bandeja
NIT_UNIV = "900123456-1"         # tenant


def b64(data: bytes) -> str:
    """Base64 sin relleno, como exige JWT."""
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def main() -> None:
    cabecera = {"alg": "HS256", "typ": "JWT"}
    cuerpo = {
        "sub": EMAIL,
        "nitUniv": NIT_UNIV,
        "roles": ["ADMIN"],
        "iat": int(time.time()),
        "exp": int(time.time()) + 60 * 60 * 24 * 365 * 5,
    }

    compacto = "{}.{}".format(
        b64(json.dumps(cabecera, separators=(",", ":")).encode()),
        b64(json.dumps(cuerpo, separators=(",", ":")).encode()),
    )
    firma = hmac.new(SECRET, compacto.encode(), hashlib.sha256).digest()

    print(f"{compacto}.{b64(firma)}")


if __name__ == "__main__":
    main()
