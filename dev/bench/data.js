window.BENCHMARK_DATA = {
  "lastUpdate": 1586526926207,
  "repoUrl": "https://github.com/1c-syntax/bsl-language-server",
  "entries": {
    "BSL LS perfomance measurement (SSL 3.1)": [
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Nikita Gryzlov",
            "username": "nixel2007"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c0053f8ce71c453afe1ff49ae44576a9cdbb3d95",
          "message": "Merge pull request #1029 from 1c-syntax/feature/benchmark\n\nWorkflow на GA для анализа конфигурации SSL 3.1",
          "timestamp": "2020-04-10T16:49:42+03:00",
          "tree_id": "70c2ab98286dc14653e79c1de071b8c77cba3852",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c0053f8ce71c453afe1ff49ae44576a9cdbb3d95"
        },
        "date": 1586526924800,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/bechmark.py::test_analyze_ssl31",
            "value": 69.08868527412415,
            "unit": "sec",
            "range": "stddev: 0",
            "extra": "mean: 69.08868527412415 sec\nrounds: 1"
          }
        ]
      }
    ]
  }
}