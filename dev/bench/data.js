window.BENCHMARK_DATA = {
  "lastUpdate": 1586784232221,
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
          "id": "6653a3b708904ab9f45c80c2a784b22d7e30c15b",
          "message": "Merge pull request #1031 from 1c-syntax/feature/benchmark-badge\n\nБейдж замеров производительности",
          "timestamp": "2020-04-11T12:18:54+03:00",
          "tree_id": "f63e3dc9fc8765d9635d244826c692e49e3a0220",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/6653a3b708904ab9f45c80c2a784b22d7e30c15b"
        },
        "date": 1586597055409,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 63.476545333862305,
            "unit": "sec",
            "range": "stddev: 0",
            "extra": "mean: 63.476545333862305 sec\nrounds: 1"
          }
        ]
      },
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
          "id": "8ac7c0591539f07e3c6d04b14771c1650ce33397",
          "message": "Merge pull request #1033 from otymko/feature/fix-benchmark\n\nFix benchmark",
          "timestamp": "2020-04-11T16:59:45+03:00",
          "tree_id": "6f84e553b2d863245d2ef8607e4dff865fea0878",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/8ac7c0591539f07e3c6d04b14771c1650ce33397"
        },
        "date": 1586613940488,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.43926119804382,
            "unit": "sec",
            "range": "stddev: 0",
            "extra": "mean: 72.43926119804382 sec\nrounds: 1"
          }
        ]
      },
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
          "id": "5eebe6dc36fb11482ceea0279253a9f1cb9f7f08",
          "message": "Merge pull request #1034 from 1c-syntax/otymko-patch-round\n\nУвеличение кругов bench с 1 до 3",
          "timestamp": "2020-04-11T17:32:47+03:00",
          "tree_id": "39fc6a8b65b34b0e9b16e8ca2795c90eb6ce93ab",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/5eebe6dc36fb11482ceea0279253a9f1cb9f7f08"
        },
        "date": 1586616026290,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 63.76906005541483,
            "unit": "sec",
            "range": "stddev: 1.2978114728391386",
            "extra": "mean: 63.76906005541483 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "olegtymko@yandex.ru",
            "name": "Oleg Tymko",
            "username": "otymko"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a96ffc5e9403a211648c232117bcd906a72bfba9",
          "message": "Total не подходит при нескольких замерах\n\ntotal -> mean",
          "timestamp": "2020-04-11T21:56:46+07:00",
          "tree_id": "4f7dae6312c530b92c95d0b8edb1049a6d987656",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/a96ffc5e9403a211648c232117bcd906a72bfba9"
        },
        "date": 1586617497388,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 69.42885907491048,
            "unit": "sec",
            "range": "stddev: 1.0543839909905468",
            "extra": "mean: 69.42885907491048 sec\nrounds: 3"
          }
        ]
      },
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
          "id": "4ba04dfa567cc70a030ccba456d44fc967aa2b89",
          "message": "Merge pull request #1039 from 1c-syntax/fix/docs\n\nИсправление в документации",
          "timestamp": "2020-04-12T13:33:05+03:00",
          "tree_id": "13d755e4b99fc6e61ee6d690f3f5426e4b56543c",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/4ba04dfa567cc70a030ccba456d44fc967aa2b89"
        },
        "date": 1586688082994,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.28481260935466,
            "unit": "sec",
            "range": "stddev: 0.6141648824262582",
            "extra": "mean: 71.28481260935466 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "nixel2007@gmail.com",
            "name": "Nikita Gryzlov",
            "username": "nixel2007"
          },
          "committer": {
            "email": "nixel2007@gmail.com",
            "name": "Nikita Gryzlov",
            "username": "nixel2007"
          },
          "distinct": true,
          "id": "f2ce76727ce3431591fe29658eb2294284cf5e41",
          "message": "Fix qf",
          "timestamp": "2020-04-13T11:10:02+03:00",
          "tree_id": "ab5d079fa5e86ef0d3d3f265416bf34a2deb730f",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/f2ce76727ce3431591fe29658eb2294284cf5e41"
        },
        "date": 1586765891385,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.39345208803813,
            "unit": "sec",
            "range": "stddev: 1.5018149040124404",
            "extra": "mean: 70.39345208803813 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "maximovvalery@gmail.com",
            "name": "Maximov Valery",
            "username": "theshadowco"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8cc23a4de7a15a2305e39ebac98ee00b825129d2",
          "message": "Merge pull request #1049 from artbear/contrib-doc-fix\n\nВ Руководство контрибьютора нет ссылки на полезную статью \"Структура диагностики, назначение и содержимое файлов\"",
          "timestamp": "2020-04-13T16:14:51+03:00",
          "tree_id": "82d35d5a605df4efe98fcb55dcd04b367b469df9",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/8cc23a4de7a15a2305e39ebac98ee00b825129d2"
        },
        "date": 1586784230733,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 78.91837930679321,
            "unit": "sec",
            "range": "stddev: 0.40390016008562496",
            "extra": "mean: 78.91837930679321 sec\nrounds: 3"
          }
        ]
      }
    ]
  }
}