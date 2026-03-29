"""
Basic AI guardrails for prompt injection and toxic/unsafe output detection.
Priority 1 from ARCHITECTURE_AND_PRODUCTION_READINESS.md
"""

import re
import logging

logger = logging.getLogger(__name__)

# Patterns that indicate prompt injection attempts
_INJECTION_PATTERNS = [
    r"ignore\s+(all\s+)?(previous|prior|above)\s+instructions",
    r"disregard\s+(all\s+)?(previous|prior|above)\s+instructions",
    r"forget\s+(all\s+)?(previous|prior|above)\s+instructions",
    r"you\s+are\s+now\s+(?:a\s+)?(?:an?\s+)?\w+\s*,?\s*not",
    r"act\s+as\s+(?:if\s+you\s+(?:are|were)\s+)?(?:a\s+)?(?:an?\s+)?\w+\s+without\s+restrictions",
    r"jailbreak",
    r"dan\s+mode",
    r"developer\s+mode",
    r"<\s*script\s*>",
    r"system\s*:\s*you\s+are",
]

# PII patterns — flag if LLM output leaks sensitive data
_PII_PATTERNS = [
    r"\b\d{3}-\d{2}-\d{4}\b",                          # SSN
    r"\b(?:\d[ -]?){13,16}\b",                          # credit card
    r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b",  # email
    r"\b(?:\+?1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b",  # phone
    r"\b(?:password|passwd|secret|api[_\s]?key)\s*[:=]\s*\S+",    # credential leak
]

# Patterns for clearly toxic/unsafe output
_TOXIC_PATTERNS = [
    r"\b(how\s+to\s+(make|build|create|synthesize)\s+(bomb|weapon|explosive|poison|malware|virus))\b",
    r"\b(step[s]?\s+(to|for)\s+(harm|kill|attack|hack))\b",
]

_INJECTION_RE = [re.compile(p, re.IGNORECASE) for p in _INJECTION_PATTERNS]
_TOXIC_RE = [re.compile(p, re.IGNORECASE) for p in _TOXIC_PATTERNS]
_PII_RE = [re.compile(p, re.IGNORECASE) for p in _PII_PATTERNS]


class GuardrailViolation(Exception):
    """Raised when a guardrail check fails."""
    def __init__(self, reason: str):
        self.reason = reason
        super().__init__(reason)


def check_input(text: str) -> None:
    """
    Check user/agent input for prompt injection attempts.
    Raises GuardrailViolation if a pattern matches.
    """
    for pattern in _INJECTION_RE:
        if pattern.search(text):
            logger.warning(f"Prompt injection pattern detected: {pattern.pattern!r}")
            raise GuardrailViolation("Input contains a disallowed instruction pattern.")


def check_output(text: str) -> None:
    """
    Check LLM output for toxic or unsafe content and PII leakage.
    Raises GuardrailViolation if a pattern matches.
    """
    for pattern in _TOXIC_RE:
        if pattern.search(text):
            logger.warning(f"Toxic output pattern detected: {pattern.pattern!r}")
            raise GuardrailViolation("Output contains unsafe content and was blocked.")
    for pattern in _PII_RE:
        if pattern.search(text):
            logger.warning(f"PII pattern detected in output: {pattern.pattern!r}")
            raise GuardrailViolation("Output may contain PII and was blocked.")


def safe_invoke(check_fn, text: str, fallback: str = "") -> str:
    """
    Run a guardrail check; return fallback string instead of raising if violated.
    Useful for output checks where you want to substitute rather than crash.
    """
    try:
        check_fn(text)
        return text
    except GuardrailViolation as e:
        logger.error(f"Guardrail blocked content: {e.reason}")
        return fallback or "[Content blocked by safety guardrail]"
