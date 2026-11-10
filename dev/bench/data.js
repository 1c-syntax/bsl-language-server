window.BENCHMARK_DATA = {
  "lastUpdate": 1794279136812,
  "repoUrl": "https://github.com/1c-syntax/bsl-language-server",
  "entries": {
    "BSL LS perfomance measurement (SSL 3.1)": [
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Kirill Chernenko",
            "username": "nixel2007"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "b765915370d1d114f2d8e961801ed3d4a97838e7",
          "message": "Merge pull request #4869 from 1c-syntax/codex/20261028-02-model\n\nfeat(types): общая модель контекстов компиляции",
          "timestamp": "2026-11-08T10:16:04+01:00",
          "tree_id": "3f12596a5dfff68accfb994a62f79b2dbabe682a",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/b765915370d1d114f2d8e961801ed3d4a97838e7"
        },
        "date": 1794136963749,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 80.9529337088267,
            "unit": "sec",
            "range": "stddev: 2.4959659810276165",
            "extra": "mean: 80.9529337088267 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Kirill Chernenko",
            "username": "nixel2007"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "d3cfbdb999a79e067886f79701be2dd017505bbd",
          "message": "Merge pull request #4920 from 1c-syntax/perf/reuse-valid-symbol-trees-20261108\n\nperf(context): переиспользование актуального дерева символов",
          "timestamp": "2026-11-08T10:31:40+01:00",
          "tree_id": "f3f1bd0cd3c497dc4e0b2160b10378e439d380a6",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/d3cfbdb999a79e067886f79701be2dd017505bbd"
        },
        "date": 1794152280353,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 76.81931781768799,
            "unit": "sec",
            "range": "stddev: 1.7782341441049017",
            "extra": "mean: 76.81931781768799 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Kirill Chernenko",
            "username": "nixel2007"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4f580fe4a038cd46a44f6c43892eb0d3a65889a8",
          "message": "Merge pull request #4870 from 1c-syntax/codex/20261028-03-inference\n\nfeat(types): вывод типов с учётом контекста вызывающего",
          "timestamp": "2026-11-08T10:47:32+01:00",
          "tree_id": "6d2eca04b64e8079a12039db651eea3e7e310390",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/4f580fe4a038cd46a44f6c43892eb0d3a65889a8"
        },
        "date": 1794155001866,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 89.33061957359314,
            "unit": "sec",
            "range": "stddev: 2.369218582126697",
            "extra": "mean: 89.33061957359314 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Nikita Fedkin",
            "username": "nixel2007"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "75eca1f95f7bda121577890d49d139f11fdd5b71",
          "message": "Merge pull request #4701 from 1c-syntax/perf/types-common-module-accessors-parsed-once\n\nСписок методов доступа к общим модулям разбирается заново только при смене настроек",
          "timestamp": "2026-11-10T03:36:07+01:00",
          "tree_id": "b650232ef6e401cac31147b7e426f8ef28c476ee",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/75eca1f95f7bda121577890d49d139f11fdd5b71"
        },
        "date": 1794279082167,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 97.11398895581563,
            "unit": "sec",
            "range": "stddev: 7.3426946817613645",
            "extra": "mean: 97.11398895581563 sec\nrounds: 3"
          }
        ]
      }
    ]
  }
}