#!/usr/bin/env python3
"""Generate random Lorem Ipsum paragraphs with occasional markdown headings."""

import random
import sys

LOREM_WORDS = [
    "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing",
    "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore",
    "et", "dolore", "magna", "aliqua", "enim", "ad", "minim", "veniam",
    "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi",
    "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure",
    "in", "reprehenderit", "voluptate", "velit", "esse", "cillum", "fugiat",
    "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat",
    "non", "proident", "sunt", "culpa", "qui", "officia", "deserunt",
    "mollit", "anim", "id", "est", "laborum", "vitae", "justo", "eget",
    "malesuada", "bibendum", "lacus", "vestibulum", "ante", "primis",
    "faucibus", "orci", "luctus", "ultrices", "posuere", "cubilia",
    "curae", "donec", "velit", "neque", "auctor", "blandit", "cursus",
    "risus", "integer", "mauris", "augue", "porta", "accumsan", "semper",
    "viverra", "nam", "libero", "tempus", "elementum", "pellentesque",
    "habitant", "morbi", "tristique", "senectus", "netus", "turpis",
    "massa", "tincidunt", "dui", "sapien", "nec", "tortor", "pretium",
    "nibh", "praesent", "gravida", "rutrum", "quisque", "sagittis",
    "purus", "maecenas", "pharetra", "convallis", "volutpat", "consequat",
]

ENGLISH_WORDS = [
    "crystal", "thunder", "garden", "phantom", "silver", "ancient",
    "digital", "forest", "golden", "hidden", "infinite", "journey",
    "kingdom", "lantern", "midnight", "northern", "ocean", "pattern",
    "quantum", "river", "shadow", "temple", "unified", "valley",
    "wandering", "zenith", "abstract", "beyond", "cascade", "drift",
    "ember", "frontier", "glacier", "horizon", "island", "junction",
    "keystone", "lighthouse", "mountain", "nebula", "orbital", "prism",
    "resonance", "spectrum", "twilight", "undertow", "vortex", "wavelength",
    "apex", "bridge", "compass", "delta", "eclipse", "falcon", "gravity",
    "harbor", "impulse", "jade", "kinetic", "lunar", "matrix", "nova",
    "obsidian", "pinnacle", "quartz", "radiant", "stellar", "tundra",
    "upward", "vibrant", "winter", "xenon", "yearning", "zephyr",
]


def generate_sentence():
    """Generate a random Lorem Ipsum sentence."""
    length = random.randint(6, 20)
    words = [random.choice(LOREM_WORDS) for _ in range(length)]
    words[0] = words[0].capitalize()
    return " ".join(words) + "."


def generate_paragraph():
    """Generate a random Lorem Ipsum paragraph."""
    num_sentences = random.randint(3, 8)
    sentences = [generate_sentence() for _ in range(num_sentences)]
    return " ".join(sentences)


def generate_heading():
    """Generate a random markdown heading (level 1-6) with three English words."""
    level = random.randint(1, 6)
    words = random.sample(ENGLISH_WORDS, 3)
    title = " ".join(w.capitalize() for w in words)
    return f"{'#' * level} {title}"


def main():
    num_paragraphs = int(sys.argv[1]) if len(sys.argv) > 1 else 20
    heading_interval = random.randint(2, 4)
    counter = 0

    # Start with a heading
    print(generate_heading())
    print()

    for i in range(num_paragraphs):
        counter += 1
        print(generate_paragraph())
        print()

        if counter >= heading_interval:
            print(generate_heading())
            print()
            counter = 0
            heading_interval = random.randint(2, 4)


if __name__ == "__main__":
    import signal
    signal.signal(signal.SIGPIPE, signal.SIG_DFL)
    try:
        main()
    except BrokenPipeError:
        pass
