import json

from anybadge import Badge


with open("output.json", encoding="utf-8") as report:
    mean = json.load(report)["benchmarks"][0]["stats"]["mean"]

Badge("Benchmark (SSL 1.0)", mean, default_color="#007ec6", value_format="%.2f").write_badge(
    "benchmark.svg", overwrite=True
)
