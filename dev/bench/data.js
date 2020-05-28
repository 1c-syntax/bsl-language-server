window.BENCHMARK_DATA = {
  "lastUpdate": 1590657784870,
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
          "id": "9976528a338c977787c19c8a624abe1af56fad04",
          "message": "Merge pull request #1053 from 1c-syntax/fix/MethodeComputer\n\nMethodeComputer fix",
          "timestamp": "2020-04-13T20:47:54+03:00",
          "tree_id": "1a7e0762333b168949b153cfa23f0cb99b52291f",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/9976528a338c977787c19c8a624abe1af56fad04"
        },
        "date": 1586800571799,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.92317056655884,
            "unit": "sec",
            "range": "stddev: 1.5978113513600387",
            "extra": "mean: 72.92317056655884 sec\nrounds: 3"
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
          "id": "dcc65a5d00115565a08ce5587409b17272e34219",
          "message": "Merge pull request #1028 from APonkratov/feature/missingVariablesDescription\n\nОтсутствует описание у переменной",
          "timestamp": "2020-04-13T20:49:07+03:00",
          "tree_id": "1aa97eb0978f135f3f6d13eab6180c72967b8902",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/dcc65a5d00115565a08ce5587409b17272e34219"
        },
        "date": 1586800646296,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 73.00902581214905,
            "unit": "sec",
            "range": "stddev: 0.8845486896504238",
            "extra": "mean: 73.00902581214905 sec\nrounds: 3"
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
          "id": "67f6969d9b8fff668b74e54732cc5e2fe68000e1",
          "message": "Merge pull request #1042 from artbear/UsingServiceTag-fix\n\nУбрал ненужный дубль проверки строки на регулярку - обработка замечания от инспекции Идеи",
          "timestamp": "2020-04-13T21:57:22+03:00",
          "tree_id": "1e5fa221b7ab3195b666812840d712d9acee2add",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/67f6969d9b8fff668b74e54732cc5e2fe68000e1"
        },
        "date": 1586804768235,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 76.9522720972697,
            "unit": "sec",
            "range": "stddev: 1.385167640697603",
            "extra": "mean: 76.9522720972697 sec\nrounds: 3"
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
          "id": "c3078ec793fe82a315a3c7e524833c0501f2107d",
          "message": "Merge pull request #1036 from 1c-syntax/feature/contribute\n\nПравки документации для разработчиков",
          "timestamp": "2020-04-13T21:56:29+03:00",
          "tree_id": "7f0e3fc23a2312a00657300e5fcc7b8082987896",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c3078ec793fe82a315a3c7e524833c0501f2107d"
        },
        "date": 1586804775557,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 83.80595016479492,
            "unit": "sec",
            "range": "stddev: 2.955904299753347",
            "extra": "mean: 83.80595016479492 sec\nrounds: 3"
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
          "id": "c2884518a39c9febd797a189e62aae557665597c",
          "message": "Merge pull request #1040 from Stepa86/fix/typo_Exceptions\n\nНовые исключения в Typo на основе проверки ERP и БСП",
          "timestamp": "2020-04-13T21:57:46+03:00",
          "tree_id": "c91a12743d909566c46c8da39c2da7e4990a0abe",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c2884518a39c9febd797a189e62aae557665597c"
        },
        "date": 1586804796764,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 64.5100724697113,
            "unit": "sec",
            "range": "stddev: 1.6384966856365597",
            "extra": "mean: 64.5100724697113 sec\nrounds: 3"
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
          "id": "0c21eba148bad6de5bfd576394b773bb12aa70e3",
          "message": "Merge pull request #1055 from 1c-syntax/fix/ContentList\n\nContentList with last whiteSpaces",
          "timestamp": "2020-04-14T00:54:10+03:00",
          "tree_id": "27c91d8a6993352478309c42f79cee2790729fa7",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/0c21eba148bad6de5bfd576394b773bb12aa70e3"
        },
        "date": 1586815342717,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.54413104057312,
            "unit": "sec",
            "range": "stddev: 0.12933625592096532",
            "extra": "mean: 72.54413104057312 sec\nrounds: 3"
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
          "id": "14696a69d01e9b4b0a80328dcafbc172cf2aec3d",
          "message": "Merge pull request #1056 from artbear/doc-auto-fix\n\nПропущенные результаты precommit - т.к. при его выполнении правильно удаляется лишний пробел",
          "timestamp": "2020-04-14T11:29:26+03:00",
          "tree_id": "708edc31b238e66f7d02b77066ecb9fc4ad4044d",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/14696a69d01e9b4b0a80328dcafbc172cf2aec3d"
        },
        "date": 1586853590629,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 66.89521845181783,
            "unit": "sec",
            "range": "stddev: 1.4015741547607594",
            "extra": "mean: 66.89521845181783 sec\nrounds: 3"
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
          "id": "bf03f7d9fe23c488fd63eb49fae44e2e6924b8f5",
          "message": "Исправил ссылку на результаты бенчмарка",
          "timestamp": "2020-04-14T15:14:36+03:00",
          "tree_id": "3d98b5d6c39e1a3a7a8844b9d8cf53b554f193ef",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/bf03f7d9fe23c488fd63eb49fae44e2e6924b8f5"
        },
        "date": 1586866968653,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.18764797846477,
            "unit": "sec",
            "range": "stddev: 0.40065979797498813",
            "extra": "mean: 70.18764797846477 sec\nrounds: 3"
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
          "id": "7d4585534c4746a2b7c7b51a199548179675b820",
          "message": "Merge pull request #1058 from 1c-syntax/feature/EOF\n\nПарсер отдает EOF в Hidden Channel",
          "timestamp": "2020-04-15T00:28:53+03:00",
          "tree_id": "0e005d4fdd7c3ed9c73b573f95f69858c8253394",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/7d4585534c4746a2b7c7b51a199548179675b820"
        },
        "date": 1586900211885,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.21630811691284,
            "unit": "sec",
            "range": "stddev: 1.4232242041767722",
            "extra": "mean: 70.21630811691284 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "int-it@yandex.ru",
            "name": "Alexey Sosnoviy",
            "username": "asosnoviy"
          },
          "committer": {
            "email": "int-it@yandex.ru",
            "name": "Alexey Sosnoviy",
            "username": "asosnoviy"
          },
          "distinct": true,
          "id": "e93883c94bac1affb541e89a15176d1645a9ad7c",
          "message": "parser version bump",
          "timestamp": "2020-04-15T10:30:43+03:00",
          "tree_id": "efa9cd397c4b1484d9f75fe56d2ebbb1744e31d0",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e93883c94bac1affb541e89a15176d1645a9ad7c"
        },
        "date": 1586936345988,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.06531023979187,
            "unit": "sec",
            "range": "stddev: 0.3590659233219285",
            "extra": "mean: 72.06531023979187 sec\nrounds: 3"
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
          "id": "0063780d0fb549ead597540c24a9978d51c2c784",
          "message": "Merge pull request #1037 from 1c-syntax/feature/thisObjectAssign\n\nthisObjectAssignDiagnostic",
          "timestamp": "2020-04-15T16:56:31+03:00",
          "tree_id": "742a016e6e8e49bcd556cbdc19802e25dc221967",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/0063780d0fb549ead597540c24a9978d51c2c784"
        },
        "date": 1586959467742,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.31435871124268,
            "unit": "sec",
            "range": "stddev: 0.301043109178605",
            "extra": "mean: 70.31435871124268 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "int-it@yandex.ru",
            "name": "Alexey Sosnoviy",
            "username": "asosnoviy"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "2378801f71267827e5abd7f9454e0c8470a67e51",
          "message": "Merge pull request #1051 from MinimaJack/cqic-fix-1\n\nAnother fix fp запрос в цикле",
          "timestamp": "2020-04-15T18:38:07+03:00",
          "tree_id": "f69b12b6699228de7691f1f36a87fe8341978b9d",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/2378801f71267827e5abd7f9454e0c8470a67e51"
        },
        "date": 1586965631417,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 74.84124422073364,
            "unit": "sec",
            "range": "stddev: 0.5293300239154064",
            "extra": "mean: 74.84124422073364 sec\nrounds: 3"
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
          "id": "bbec2f0aec1afa221a38250a0cf2fc40ca6dffec",
          "message": "Merge pull request #1059 from 1c-syntax/feature/silent\n\nДобавил молчаливый режим консоли",
          "timestamp": "2020-04-15T19:07:49+03:00",
          "tree_id": "d262d82374d3cc3e612322b8d4852fdadbe73c2e",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/bbec2f0aec1afa221a38250a0cf2fc40ca6dffec"
        },
        "date": 1586967440797,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 81.59028649330139,
            "unit": "sec",
            "range": "stddev: 1.3442582992913759",
            "extra": "mean: 81.59028649330139 sec\nrounds: 3"
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
          "id": "c878e4a1efd1b43bc6972f7e2b7cb173a042fa4a",
          "message": "Merge pull request #1062 from 1c-syntax/fix/eofParseError\n\nEOF parse error fix",
          "timestamp": "2020-04-16T14:47:39+03:00",
          "tree_id": "25ef8da1c1a30d6a944d8af1bbb5d09e80bb854f",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c878e4a1efd1b43bc6972f7e2b7cb173a042fa4a"
        },
        "date": 1587038084583,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 60.420881589253746,
            "unit": "sec",
            "range": "stddev: 0.892696717753229",
            "extra": "mean: 60.420881589253746 sec\nrounds: 3"
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
          "id": "fcdc725635065beb83376065eb6de5979b949c41",
          "message": "Merge pull request #1064 from 1c-syntax/fix/variableKind\n\nchange variableKind to Module",
          "timestamp": "2020-04-16T17:19:49+03:00",
          "tree_id": "d77f76be4418ddfb37bb88a355830577bb5d9401",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/fcdc725635065beb83376065eb6de5979b949c41"
        },
        "date": 1587047335465,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 73.69795568784077,
            "unit": "sec",
            "range": "stddev: 2.7807064572982356",
            "extra": "mean: 73.69795568784077 sec\nrounds: 3"
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
          "id": "e8de04f671f28ac74c3086367d8114c9c1abccb4",
          "message": "Merge pull request #1063 from 1c-syntax/fix/uselessFoeEach\n\nuseLessForeach field FP fix",
          "timestamp": "2020-04-16T17:34:17+03:00",
          "tree_id": "20e257ce2bbdd909d8ef5fc191aa3947810fd01e",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e8de04f671f28ac74c3086367d8114c9c1abccb4"
        },
        "date": 1587048135816,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 68.57601547241211,
            "unit": "sec",
            "range": "stddev: 1.4638553938007226",
            "extra": "mean: 68.57601547241211 sec\nrounds: 3"
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
          "id": "ce01eeadad4f15cf2a7cc68aaea24437e33f1028",
          "message": "Merge pull request #1067 from 1c-syntax/feature/config_for_bench\n\nWIP: Исключение из замера диагностики Type + исправление опечатки",
          "timestamp": "2020-04-17T00:32:35+03:00",
          "tree_id": "926d6b5d7e9d70546acd7adf9df36ce891516e52",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/ce01eeadad4f15cf2a7cc68aaea24437e33f1028"
        },
        "date": 1587073168284,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 55.04965249697367,
            "unit": "sec",
            "range": "stddev: 0.39911953056612615",
            "extra": "mean: 55.04965249697367 sec\nrounds: 3"
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
          "id": "df525816eaed68f64e988317d9150e9d2778dd21",
          "message": "Merge pull request #1086 from 1c-syntax/feature/issue1079\n\nОбменДанными.Загрузка в обработчиках событий объекта",
          "timestamp": "2020-04-20T17:49:41+03:00",
          "tree_id": "7fa8436d937cead1f629a9893268a89c4ebcc940",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/df525816eaed68f64e988317d9150e9d2778dd21"
        },
        "date": 1587394587171,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 54.360833485921226,
            "unit": "sec",
            "range": "stddev: 0.4788611610911804",
            "extra": "mean: 54.360833485921226 sec\nrounds: 3"
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
          "id": "57660172dddc7ce045da58c489118fd50cb17240",
          "message": "Merge pull request #1093 from 1c-syntax/feature/issue1079\n\nIssue1079 / Фикс регулярки + map to flatmap (избавился от get)",
          "timestamp": "2020-04-20T19:15:19+03:00",
          "tree_id": "adace9e276d299d4848da2efb90c476bc1cc4dfc",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/57660172dddc7ce045da58c489118fd50cb17240"
        },
        "date": 1587399710353,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 53.85562459627787,
            "unit": "sec",
            "range": "stddev: 1.0383059448663565",
            "extra": "mean: 53.85562459627787 sec\nrounds: 3"
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
          "id": "27c95d8bb19260140035278b88cba79fd7de3319",
          "message": "Merge pull request #1094 from artbear/dot-reg-fix\n\nНеточные регулярки - неточное использование точки",
          "timestamp": "2020-04-20T21:14:45+03:00",
          "tree_id": "348bb2c3367bdf1f95d081cbe6252fb8d6873ad1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/27c95d8bb19260140035278b88cba79fd7de3319"
        },
        "date": 1587406890527,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 54.797364950180054,
            "unit": "sec",
            "range": "stddev: 0.6695273614546695",
            "extra": "mean: 54.797364950180054 sec\nrounds: 3"
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
          "id": "4db0e660a76d8356d800f2c4b3b280d9f6be9f99",
          "message": "Merge pull request #1097 from qtLex/feature/CanceledStandartInteractionWithAutomatedTestingTools\n\nFeature/canceled standart interaction with automated testing tools",
          "timestamp": "2020-04-20T23:35:06+03:00",
          "tree_id": "097d1a46335a816662bd82ab2dc028ee8fbf69cb",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/4db0e660a76d8356d800f2c4b3b280d9f6be9f99"
        },
        "date": 1587415308721,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 53.12495541572571,
            "unit": "sec",
            "range": "stddev: 1.2134717858871449",
            "extra": "mean: 53.12495541572571 sec\nrounds: 3"
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
          "id": "db66398eadec27726e62b6832401b8490bdbf387",
          "message": "Merge pull request #1095 from 1c-syntax/fix/npeDataLoad\n\nDataExchangeLoadingDiagnostic npe fix",
          "timestamp": "2020-04-21T11:50:01+03:00",
          "tree_id": "5e9490dcd0d3c9e9161964b62405b260543dcfcd",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/db66398eadec27726e62b6832401b8490bdbf387"
        },
        "date": 1587459404376,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 52.9276594320933,
            "unit": "sec",
            "range": "stddev: 0.08179733693368213",
            "extra": "mean: 52.9276594320933 sec\nrounds: 3"
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
          "id": "07e5f43885aa67e2d0ca390e3791a083dabf1aad",
          "message": "Merge pull request #1099 from 1c-syntax/fix/fpStartTransaction\n\nbegin transaction fp fix",
          "timestamp": "2020-04-21T11:51:12+03:00",
          "tree_id": "e1f462949c89a49ea3325537e9c0d6abbda1d42d",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/07e5f43885aa67e2d0ca390e3791a083dabf1aad"
        },
        "date": 1587459437752,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 47.79250224431356,
            "unit": "sec",
            "range": "stddev: 1.6354533051298585",
            "extra": "mean: 47.79250224431356 sec\nrounds: 3"
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
          "id": "7336a382fdbd3b0a562065cbbee139f21d546f5c",
          "message": "Merge pull request #1096 from qtLex/fix/fp-multilingual-string-has-all-declared-languages-diagnostic\n\nFix FP MultilingualStringHasAllDeclaredLanguages",
          "timestamp": "2020-04-21T11:52:05+03:00",
          "tree_id": "300689aed38992f59fd0cbd98488fd9ce8351c4b",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/7336a382fdbd3b0a562065cbbee139f21d546f5c"
        },
        "date": 1587459499402,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 49.482404470443726,
            "unit": "sec",
            "range": "stddev: 0.6442960886777359",
            "extra": "mean: 49.482404470443726 sec\nrounds: 3"
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
          "id": "5dd54a932f60ad54c04307542893050ef82a2fb6",
          "message": "Merge pull request #1080 from 1c-syntax/feature/commonModuleAssign\n\nCommonModuleAssignDiagnostic",
          "timestamp": "2020-04-21T14:34:56+03:00",
          "tree_id": "e1deaea1c97973b7718683aedf0643dd75010b42",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/5dd54a932f60ad54c04307542893050ef82a2fb6"
        },
        "date": 1587469268065,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 49.750128428141274,
            "unit": "sec",
            "range": "stddev: 0.5562564220534881",
            "extra": "mean: 49.750128428141274 sec\nrounds: 3"
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
          "id": "08fa325dc54a260591506e9bde58eb781e4408c5",
          "message": "Merge pull request #1082 from artbear/InvalidCharacterInFileDiagnostic-fix\n\nWIP:Ускорение InvalidCharacterInFileDiagnostic",
          "timestamp": "2020-04-21T14:47:42+03:00",
          "tree_id": "64ea6cf8ee3f1cc0caa09e2ed5e2f4fd13f14f49",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/08fa325dc54a260591506e9bde58eb781e4408c5"
        },
        "date": 1587470016436,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 47.55061054229736,
            "unit": "sec",
            "range": "stddev: 0.9742040613489666",
            "extra": "mean: 47.55061054229736 sec\nrounds: 3"
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
          "id": "8f653ac77e735786df66d2a85891db25e2e246c9",
          "message": "Merge branch 'ConsecutiveEmptyLines-958' into develop",
          "timestamp": "2020-04-21T17:54:06+03:00",
          "tree_id": "fcf7801e5deabf575d2f07b8a2858f8955b20071",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/8f653ac77e735786df66d2a85891db25e2e246c9"
        },
        "date": 1587481294149,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 54.94559637705485,
            "unit": "sec",
            "range": "stddev: 0.7462291621003748",
            "extra": "mean: 54.94559637705485 sec\nrounds: 3"
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
          "id": "08fa325dc54a260591506e9bde58eb781e4408c5",
          "message": "Merge pull request #1082 from artbear/InvalidCharacterInFileDiagnostic-fix\n\nWIP:Ускорение InvalidCharacterInFileDiagnostic",
          "timestamp": "2020-04-21T14:47:42+03:00",
          "tree_id": "64ea6cf8ee3f1cc0caa09e2ed5e2f4fd13f14f49",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/08fa325dc54a260591506e9bde58eb781e4408c5"
        },
        "date": 1587481335361,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 51.09258484840393,
            "unit": "sec",
            "range": "stddev: 0.7268870229557479",
            "extra": "mean: 51.09258484840393 sec\nrounds: 3"
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
          "id": "c9c8e9c4bfed08a2d3d013d596fee6abfefb1134",
          "message": "Merge branch 'ConsecutiveEmptyLines-958' into develop",
          "timestamp": "2020-04-21T18:04:45+03:00",
          "tree_id": "c1c67bb082f510869ae618d5c04cb1163a42d9b1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c9c8e9c4bfed08a2d3d013d596fee6abfefb1134"
        },
        "date": 1587481939233,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 57.288793881734215,
            "unit": "sec",
            "range": "stddev: 1.19734789369545",
            "extra": "mean: 57.288793881734215 sec\nrounds: 3"
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
          "id": "775f92101bc1ef442ad3c2b5b07acc78b757a85a",
          "message": "Merge branch 'feature/diagnosticMode' into develop",
          "timestamp": "2020-04-21T23:26:02+03:00",
          "tree_id": "80ba6d2c27a0f7bd14fe3b2dd21ed4f90cf33af0",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/775f92101bc1ef442ad3c2b5b07acc78b757a85a"
        },
        "date": 1587500924647,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 2.31528107325236,
            "unit": "sec",
            "range": "stddev: 0.058392579807114715",
            "extra": "mean: 2.31528107325236 sec\nrounds: 3"
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
          "id": "538e995dda77051e94b3afcff83911ac7b27e6e7",
          "message": "Merge pull request #1103 from 1c-syntax/fix/docFix\n\ndoc fix",
          "timestamp": "2020-04-22T10:54:05+03:00",
          "tree_id": "8bbaba019dde5f548238ad745d449d821ffe578f",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/538e995dda77051e94b3afcff83911ac7b27e6e7"
        },
        "date": 1587542186443,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 2.2734886010487876,
            "unit": "sec",
            "range": "stddev: 0.013718933091712479",
            "extra": "mean: 2.2734886010487876 sec\nrounds: 3"
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
          "id": "048ab6f23c2dac5741b9e33dfe8cad5545f86a34",
          "message": "Merge pull request #1106 from 1c-syntax/fix/benchmark\n\nИсправление генерации конфига для девелоп-версии",
          "timestamp": "2020-04-22T14:51:04+03:00",
          "tree_id": "ecdfa6b6a76568c36910900074c0ae19516c7a81",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/048ab6f23c2dac5741b9e33dfe8cad5545f86a34"
        },
        "date": 1587556759554,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.37626043955485,
            "unit": "sec",
            "range": "stddev: 0.30817895444650345",
            "extra": "mean: 71.37626043955485 sec\nrounds: 3"
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
          "id": "76fcf12f7d4983a508893bf2848238b70692dc44",
          "message": "Merge pull request #1100 from yukon39/feature/CoverageAnalysis\n\nДобавлена возможность по выводу в отчет строк требующих покрытия",
          "timestamp": "2020-04-22T14:52:11+03:00",
          "tree_id": "563281f2a9b78d2c8704bd0ff4c18be558531d51",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/76fcf12f7d4983a508893bf2848238b70692dc44"
        },
        "date": 1587556816456,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.45123958587646,
            "unit": "sec",
            "range": "stddev: 0.9369369316018011",
            "extra": "mean: 71.45123958587646 sec\nrounds: 3"
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
          "id": "61e7eae8db3e8f7b9cef24465c28f4f0b6135dd1",
          "message": "Merge pull request #1107 from EightM/feature/deprecatedMethods8310\n\nFeature/deprecated methods8310",
          "timestamp": "2020-04-23T11:52:05+03:00",
          "tree_id": "3c9bdfb123bbb7420d301dfc8b9f435f6ac25ba1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/61e7eae8db3e8f7b9cef24465c28f4f0b6135dd1"
        },
        "date": 1587632438817,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 74.93175689379375,
            "unit": "sec",
            "range": "stddev: 1.0188496583787248",
            "extra": "mean: 74.93175689379375 sec\nrounds: 3"
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
          "id": "260b5648eaf474716fa8c78f8d865f1d7de5fd5b",
          "message": "Merge pull request #1085 from 1c-syntax/feature/picocli\n\nПереход на CLI библиотеку picocli",
          "timestamp": "2020-04-23T13:24:21+03:00",
          "tree_id": "142cc09d630d318e4a7d38e932ff7774c4e70277",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/260b5648eaf474716fa8c78f8d865f1d7de5fd5b"
        },
        "date": 1587637948126,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.88965392112732,
            "unit": "sec",
            "range": "stddev: 0.0823066300053433",
            "extra": "mean: 70.88965392112732 sec\nrounds: 3"
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
          "id": "c6cd9ef684918e005969557a3e77591a893fbd5c",
          "message": "Merge pull request #1111 from 1c-syntax/feature/fixCmdParam\n\nFix PicoCli default command",
          "timestamp": "2020-04-24T10:57:18+03:00",
          "tree_id": "cc5a2b1419ebf2c838fd1bc0e136a8c6d8ce6794",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c6cd9ef684918e005969557a3e77591a893fbd5c"
        },
        "date": 1587715504415,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 68.05290834108989,
            "unit": "sec",
            "range": "stddev: 1.005434225336687",
            "extra": "mean: 68.05290834108989 sec\nrounds: 3"
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
          "id": "64bfa1dad50fed65f8b9101c9c0355e2a38636ff",
          "message": "Merge pull request #1108 from 1c-syntax/fix/npeDataLoad",
          "timestamp": "2020-04-24T12:24:08+03:00",
          "tree_id": "bb8385b45cfc84ef2b61c5400d52a8ffabff0bd6",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/64bfa1dad50fed65f8b9101c9c0355e2a38636ff"
        },
        "date": 1587720721148,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 69.79234504699707,
            "unit": "sec",
            "range": "stddev: 2.306702603784556",
            "extra": "mean: 69.79234504699707 sec\nrounds: 3"
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
          "id": "d76a5cc70224def759bc47e3713dcc7b4045e6a2",
          "message": "Merge pull request #1109 from qtLex/fix/ge-tokens-in-diagnostic",
          "timestamp": "2020-04-24T15:24:33+03:00",
          "tree_id": "84e5d33c3ae0ea5103466704edd622caefb4c81b",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/d76a5cc70224def759bc47e3713dcc7b4045e6a2"
        },
        "date": 1587731538463,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 66.50787218411763,
            "unit": "sec",
            "range": "stddev: 1.1706573645579375",
            "extra": "mean: 66.50787218411763 sec\nrounds: 3"
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
          "id": "ccd796514ec427b4e92bf6b3f5d7625119dd2e25",
          "message": "Update Typo.md",
          "timestamp": "2020-04-24T17:02:44+03:00",
          "tree_id": "97682cdd66acd39ae8b111df0adb29103b78f8f9",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/ccd796514ec427b4e92bf6b3f5d7625119dd2e25"
        },
        "date": 1587737496270,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 78.64872137705485,
            "unit": "sec",
            "range": "stddev: 1.2680966229533488",
            "extra": "mean: 78.64872137705485 sec\nrounds: 3"
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
          "id": "a03b96328bbc925700c00f138cf0581974341311",
          "message": "Уточнение справки по Typo",
          "timestamp": "2020-04-24T17:05:00+03:00",
          "tree_id": "45fe21b2c623a8df0f51aea8c6fe1b09eb5208b2",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/a03b96328bbc925700c00f138cf0581974341311"
        },
        "date": 1587737586665,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 67.9976216952006,
            "unit": "sec",
            "range": "stddev: 1.6640163262313228",
            "extra": "mean: 67.9976216952006 sec\nrounds: 3"
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
          "id": "36e015d9d6ead85cfb6883dd898e033bdfa70877",
          "message": "Исправление документации",
          "timestamp": "2020-04-24T17:08:18+03:00",
          "tree_id": "84803fc442d481fa0f9a7e9f43fc9ef9cec2b2e1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/36e015d9d6ead85cfb6883dd898e033bdfa70877"
        },
        "date": 1587737766940,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 66.34760117530823,
            "unit": "sec",
            "range": "stddev: 1.2466249326730545",
            "extra": "mean: 66.34760117530823 sec\nrounds: 3"
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
          "id": "25fe67793cca0e71d70f182592b8bd0eceb15ba0",
          "message": "Merge pull request #1119 from yukon39/feature/AnalysisWorkspaceOption",
          "timestamp": "2020-04-26T13:35:55+03:00",
          "tree_id": "7064c39cc6d9c8f226da104098cb48c5288a63cd",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/25fe67793cca0e71d70f182592b8bd0eceb15ba0"
        },
        "date": 1587897815161,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 67.53630415598552,
            "unit": "sec",
            "range": "stddev: 1.640251797034401",
            "extra": "mean: 67.53630415598552 sec\nrounds: 3"
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
          "id": "41ad0e89986681a5678d6fab3d1eca08997ad61d",
          "message": "Merge pull request #1116 from EightM/feature/DeprecatedAttributes8312",
          "timestamp": "2020-04-26T14:07:45+03:00",
          "tree_id": "acf6088917c61658f2c429419e09e6674ab2de92",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/41ad0e89986681a5678d6fab3d1eca08997ad61d"
        },
        "date": 1587899752890,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.80062262217204,
            "unit": "sec",
            "range": "stddev: 1.3655075404783061",
            "extra": "mean: 71.80062262217204 sec\nrounds: 3"
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
          "id": "286cb6f15d07b58cab92212cfee8b576eff3d5b8",
          "message": "Merge pull request #1135 from EightM/fix/8310DiagnosticRefactor\n\nДобавлено явное указание локалей при вызове toLowerCase()",
          "timestamp": "2020-04-26T16:05:11+03:00",
          "tree_id": "f3eb28f140722e4b969e2511a35b87605beb4677",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/286cb6f15d07b58cab92212cfee8b576eff3d5b8"
        },
        "date": 1587906758319,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 65.36535310745239,
            "unit": "sec",
            "range": "stddev: 0.89016860179428",
            "extra": "mean: 65.36535310745239 sec\nrounds: 3"
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
          "id": "63eb898b50cdd8f81491b776ec83a51548a4a723",
          "message": "Update index.md",
          "timestamp": "2020-04-27T13:12:55+03:00",
          "tree_id": "f92b60675d6073b4e512d341535438065310872c",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/63eb898b50cdd8f81491b776ec83a51548a4a723"
        },
        "date": 1587982827558,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 65.56008839607239,
            "unit": "sec",
            "range": "stddev: 2.051941302948473",
            "extra": "mean: 65.56008839607239 sec\nrounds: 3"
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
          "id": "3bd9829aacb3a0ae3a4fce32db7c1c58e2d638c3",
          "message": "Create StyleGuide.md",
          "timestamp": "2020-04-27T13:12:30+03:00",
          "tree_id": "fc7a26227541dfcd84a9446ba87fe64557356a8e",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/3bd9829aacb3a0ae3a4fce32db7c1c58e2d638c3"
        },
        "date": 1587982848530,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.27851271629333,
            "unit": "sec",
            "range": "stddev: 0.6916317825982995",
            "extra": "mean: 72.27851271629333 sec\nrounds: 3"
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
          "id": "59844692c03891a3a09dde85c104e9c2d8008ed1",
          "message": "Update index.md",
          "timestamp": "2020-04-27T13:14:49+03:00",
          "tree_id": "4db923c18e48aa436f17107e745e5ec529605011",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/59844692c03891a3a09dde85c104e9c2d8008ed1"
        },
        "date": 1587982985255,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 65.27453978856404,
            "unit": "sec",
            "range": "stddev: 2.0403107877432274",
            "extra": "mean: 65.27453978856404 sec\nrounds: 3"
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
          "id": "9b596f3d90f711ed6ffdc3ed06fab530f8b9a231",
          "message": "Merge pull request #1087 from 1c-syntax/feature/commonModuleNameClientServer\n\nCommonModuleNameDiagnostics",
          "timestamp": "2020-04-28T09:03:21+03:00",
          "tree_id": "b5553ab9e35b529f0a98b7a110b2551297ac22d4",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/9b596f3d90f711ed6ffdc3ed06fab530f8b9a231"
        },
        "date": 1588054297648,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.77817352612813,
            "unit": "sec",
            "range": "stddev: 1.288936171189103",
            "extra": "mean: 71.77817352612813 sec\nrounds: 3"
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
          "id": "748d17b8cf48fd42ef9159679e91695920c5b761",
          "message": "Merge pull request #1118 from 1c-syntax/feature/issue110\n\nФункция всегда возвращает одно и то же примитивное значение",
          "timestamp": "2020-04-28T09:38:53+03:00",
          "tree_id": "ff14d104af535ebe9b4a581c85fe92d141ff223c",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/748d17b8cf48fd42ef9159679e91695920c5b761"
        },
        "date": 1588056411131,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 69.50534439086914,
            "unit": "sec",
            "range": "stddev: 1.5919201491573978",
            "extra": "mean: 69.50534439086914 sec\nrounds: 3"
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
          "id": "ba6b79994660dcb3246d8f2d59419e50e9b97caf",
          "message": "Merge pull request #1143 from EightM/feature/CompatibilityMode8317",
          "timestamp": "2020-04-29T12:40:52+03:00",
          "tree_id": "6b39408eeb66304e6542edaae3ee2f9b1bb0c2bb",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/ba6b79994660dcb3246d8f2d59419e50e9b97caf"
        },
        "date": 1588153732268,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.97816332181294,
            "unit": "sec",
            "range": "stddev: 0.5137954495346785",
            "extra": "mean: 70.97816332181294 sec\nrounds: 3"
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
          "id": "4fbbcf2682bd2cefc565174aca06fd2f4ea63e2b",
          "message": "Merge pull request #1140 from EightM/feature/IsInRoleDiagnostic\n\nНовая диагностика: Запрет на использование РольДоступна",
          "timestamp": "2020-04-29T17:51:05+03:00",
          "tree_id": "b2f56e82fc47cfb8c6c4a388adc56850327fa15c",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/4fbbcf2682bd2cefc565174aca06fd2f4ea63e2b"
        },
        "date": 1588172388543,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 78.00321968396504,
            "unit": "sec",
            "range": "stddev: 0.8243297359563875",
            "extra": "mean: 78.00321968396504 sec\nrounds: 3"
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
          "id": "cdb3b92f38b174c39317a0f26c4d2f0692c96eea",
          "message": "Merge pull request #1141 from EightM/feature/UnsafeSafeModeMethodCall\n\nНовая диагностика: Ошибка неявного преобразования БезопасныйРежим",
          "timestamp": "2020-04-29T17:57:47+03:00",
          "tree_id": "7dc4d97b0c3482d00c762628c0b5b7d98f56c53d",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/cdb3b92f38b174c39317a0f26c4d2f0692c96eea"
        },
        "date": 1588172759540,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.17470502853394,
            "unit": "sec",
            "range": "stddev: 0.8189776048463295",
            "extra": "mean: 72.17470502853394 sec\nrounds: 3"
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
          "id": "54eac6d3d4b4eec674581d98f6ac1b0bbbe151df",
          "message": "Merge pull request #1145 from MinimaJack/cleanup\n\nSome cleanups.",
          "timestamp": "2020-04-29T22:42:10+03:00",
          "tree_id": "bdfe4a2560a1d79d65e87b4e553452b4ec7a29cf",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/54eac6d3d4b4eec674581d98f6ac1b0bbbe151df"
        },
        "date": 1588189799412,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.31305559476216,
            "unit": "sec",
            "range": "stddev: 0.3988113302838267",
            "extra": "mean: 70.31305559476216 sec\nrounds: 3"
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
          "id": "6e47b3f2f0c43a1395520b0c3b61b75f088cc421",
          "message": "javadoc",
          "timestamp": "2020-05-02T00:21:35+03:00",
          "tree_id": "bfb98938ea58b2c1596cffbf9c1168db1097e1fc",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/6e47b3f2f0c43a1395520b0c3b61b75f088cc421"
        },
        "date": 1588368589820,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.67260773976643,
            "unit": "sec",
            "range": "stddev: 0.3153325608421763",
            "extra": "mean: 72.67260773976643 sec\nrounds: 3"
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
          "id": "0b031d46219e4e2802ee592c89ed1ce8b23fcd59",
          "message": "javadoc 14",
          "timestamp": "2020-05-02T00:40:07+03:00",
          "tree_id": "1e15d3cf0457bcaeb6769263992041a01aa33b2e",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/0b031d46219e4e2802ee592c89ed1ce8b23fcd59"
        },
        "date": 1588369704933,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 73.31069755554199,
            "unit": "sec",
            "range": "stddev: 0.9990940629347301",
            "extra": "mean: 73.31069755554199 sec\nrounds: 3"
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
          "id": "d167b92abefeb92e464cff72d5ba245392c5d59a",
          "message": "Обновление сайта при изменении файла сборки",
          "timestamp": "2020-05-02T00:43:03+03:00",
          "tree_id": "260e5236697bd8c385a03b4b8f0026348d0f5b96",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/d167b92abefeb92e464cff72d5ba245392c5d59a"
        },
        "date": 1588369833673,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 65.69640644391377,
            "unit": "sec",
            "range": "stddev: 0.3607976156034893",
            "extra": "mean: 65.69640644391377 sec\nrounds: 3"
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
          "id": "ecf358e7bcd3b60683ec1bd2edb3c7196f07940b",
          "message": "Игнор пользовательского файла",
          "timestamp": "2020-05-02T00:45:32+03:00",
          "tree_id": "28dc064c455667b91a567f3e40a12aecd93cb4e8",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/ecf358e7bcd3b60683ec1bd2edb3c7196f07940b"
        },
        "date": 1588370018952,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.49336361885071,
            "unit": "sec",
            "range": "stddev: 2.374931673238364",
            "extra": "mean: 72.49336361885071 sec\nrounds: 3"
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
          "id": "9e0fc7e36a0e1d00393e1bb415dca243725d8fa6",
          "message": "Пустой файл в javadoc-каталоге",
          "timestamp": "2020-05-02T00:50:43+03:00",
          "tree_id": "a4ab6cf23aca113f3703bcc673e64e7f334af0e1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/9e0fc7e36a0e1d00393e1bb415dca243725d8fa6"
        },
        "date": 1588370309204,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 68.59183382987976,
            "unit": "sec",
            "range": "stddev: 0.18575034386042363",
            "extra": "mean: 68.59183382987976 sec\nrounds: 3"
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
          "id": "cc9a538ea0017ccaf3c2d7e9b2f6e2af1337bf8e",
          "message": ".nojekyll\n\nhttps://github.blog/2009-12-29-bypassing-jekyll-on-github-pages/",
          "timestamp": "2020-05-02T01:11:42+03:00",
          "tree_id": "a3b6f558309292b70997137f480d4e7c45ff1427",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/cc9a538ea0017ccaf3c2d7e9b2f6e2af1337bf8e"
        },
        "date": 1588371583178,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.05538058280945,
            "unit": "sec",
            "range": "stddev: 0.21497218230339915",
            "extra": "mean: 71.05538058280945 sec\nrounds: 3"
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
          "id": "030f3ad6e1e550c3cfdbb9b653c3109aed6902ee",
          "message": "Ускорение сборки",
          "timestamp": "2020-05-02T01:14:52+03:00",
          "tree_id": "cf35c7c0a9c78e1527dc082e46193a97fc35a410",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/030f3ad6e1e550c3cfdbb9b653c3109aed6902ee"
        },
        "date": 1588371748794,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 66.44907506306966,
            "unit": "sec",
            "range": "stddev: 1.0017832403869364",
            "extra": "mean: 66.44907506306966 sec\nrounds: 3"
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
          "id": "bdcad7ae9f368f583016f9c4836d817c730169b7",
          "message": "Create .nojekyll",
          "timestamp": "2020-05-02T01:35:01+03:00",
          "tree_id": "e16d9a6ffd592028d0e4edfc93e7fcf1f2e0a98e",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/bdcad7ae9f368f583016f9c4836d817c730169b7"
        },
        "date": 1588372968523,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 68.47536174456279,
            "unit": "sec",
            "range": "stddev: 0.8802504032516547",
            "extra": "mean: 68.47536174456279 sec\nrounds: 3"
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
          "id": "0d82c612b40bc66474d59bba0eaa1cd009abe926",
          "message": "Delete .nojekyll",
          "timestamp": "2020-05-02T01:35:19+03:00",
          "tree_id": "ded7ac400c0de81347509985647fb20b96619a12",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/0d82c612b40bc66474d59bba0eaa1cd009abe926"
        },
        "date": 1588373005775,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.3581714630127,
            "unit": "sec",
            "range": "stddev: 0.6342149010028914",
            "extra": "mean: 72.3581714630127 sec\nrounds: 3"
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
          "id": "47c12efdd3f24a948b32e958c469f31aca880b88",
          "message": "ПКМ по file",
          "timestamp": "2020-05-02T13:49:24+03:00",
          "tree_id": "0ab6aecf932fa51bd0304ab92772a9a3ef0aeaf2",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/47c12efdd3f24a948b32e958c469f31aca880b88"
        },
        "date": 1588417089270,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 77.90257573127747,
            "unit": "sec",
            "range": "stddev: 0.20575371514416912",
            "extra": "mean: 77.90257573127747 sec\nrounds: 3"
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
          "id": "c8c60feacba528cc556878322f1286e956889840",
          "message": "Create _config.yml",
          "timestamp": "2020-05-02T18:20:25+07:00",
          "tree_id": "815b2a3e027b8d16809833dac1aef9faa90d8873",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/c8c60feacba528cc556878322f1286e956889840"
        },
        "date": 1588418913662,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.67603039741516,
            "unit": "sec",
            "range": "stddev: 0.42127758319336694",
            "extra": "mean: 70.67603039741516 sec\nrounds: 3"
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
          "id": "731db7eaaeacdd475654bce6d7c5335e5f13a073",
          "message": "Merge pull request #1148 from 1c-syntax/feature/clever-jekyll\n\nCreate .nojekyll",
          "timestamp": "2020-05-02T18:39:18+07:00",
          "tree_id": "08ac4e351b18e62c94e9f2f4dae387a26f4ca738",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/731db7eaaeacdd475654bce6d7c5335e5f13a073"
        },
        "date": 1588420031808,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.39842669169109,
            "unit": "sec",
            "range": "stddev: 1.8918903557312257",
            "extra": "mean: 70.39842669169109 sec\nrounds: 3"
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
          "id": "6c75d1f4305fe88ca3344c5991d8582a550278ca",
          "message": "Merge pull request #1149 from 1c-syntax/gh-pages-nojekyll\n\nСоздаем nojekyll при deploy gh-pages",
          "timestamp": "2020-05-02T15:55:26+03:00",
          "tree_id": "7466cb7b366732019aa5b1da19623b5c92243d1f",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/6c75d1f4305fe88ca3344c5991d8582a550278ca"
        },
        "date": 1588424621311,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 72.6755895614624,
            "unit": "sec",
            "range": "stddev: 1.6874321852845617",
            "extra": "mean: 72.6755895614624 sec\nrounds: 3"
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
          "id": "eac1354e76117875729185c524fd80c6602bc89c",
          "message": "Merge pull request #1151 from EightM/feature/UnsafeSafeModeDiagnosticRefactor",
          "timestamp": "2020-05-03T15:23:09+03:00",
          "tree_id": "0ad5a604a583003de66780c8d18e34287deed5ab",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/eac1354e76117875729185c524fd80c6602bc89c"
        },
        "date": 1588509037009,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 66.01235747337341,
            "unit": "sec",
            "range": "stddev: 2.4343955821686207",
            "extra": "mean: 66.01235747337341 sec\nrounds: 3"
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
          "id": "028099dec5f87b1a3a84e9ff1f0253afcef13f53",
          "message": "Merge pull request #1144 from EightM/feature/DeprecatedMethods8317",
          "timestamp": "2020-05-03T16:27:21+03:00",
          "tree_id": "c487ed789922060ffbca66e067248e9e810c32ba",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/028099dec5f87b1a3a84e9ff1f0253afcef13f53"
        },
        "date": 1588512945148,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 75.81446552276611,
            "unit": "sec",
            "range": "stddev: 0.9628556537001249",
            "extra": "mean: 75.81446552276611 sec\nrounds: 3"
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
          "id": "942cc1d4897b8aebcac22f59e40603bb9f049b77",
          "message": "Оптимизация getDescendants\n\nЕдиноразовое создание List для хранения результата дает ускорение примерно в 4 раза.",
          "timestamp": "2020-05-03T17:31:43+03:00",
          "tree_id": "d137c860916c18911f749776252f708eadc68f4b",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/942cc1d4897b8aebcac22f59e40603bb9f049b77"
        },
        "date": 1588516744227,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 62.893169482549034,
            "unit": "sec",
            "range": "stddev: 0.8466044779767242",
            "extra": "mean: 62.893169482549034 sec\nrounds: 3"
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
          "id": "fe382eda6b2d1587be55e2133adc5c32b26366bc",
          "message": "Избавление от ParseTree в Symbol",
          "timestamp": "2020-05-04T21:19:29+03:00",
          "tree_id": "3cf3c36dfc1cf3018c0bfb83f3a314c62fdcea57",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/fe382eda6b2d1587be55e2133adc5c32b26366bc"
        },
        "date": 1588616810877,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 62.16158072153727,
            "unit": "sec",
            "range": "stddev: 4.661411203245504",
            "extra": "mean: 62.16158072153727 sec\nrounds: 3"
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
          "id": "085fd9afb5628ecbdaf7cf5f5008138e637d0165",
          "message": "Merge pull request #1153 from MinimaJack/er-fx-1",
          "timestamp": "2020-05-05T11:54:25+03:00",
          "tree_id": "536d94976f7b2d27724d4ee29723e1d6ad6bb210",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/085fd9afb5628ecbdaf7cf5f5008138e637d0165"
        },
        "date": 1588669365025,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.4718307654063,
            "unit": "sec",
            "range": "stddev: 3.2004369827808765",
            "extra": "mean: 71.4718307654063 sec\nrounds: 3"
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
          "id": "79f3efac1adc329bb609d0c226572e7f9f3cd3f8",
          "message": "Merge pull request #1152 from 1c-syntax/feature/deprecatedSymbolInfo",
          "timestamp": "2020-05-06T13:36:32+03:00",
          "tree_id": "604cfec2e8ea75c23bbd55082fc10bacef592b6a",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/79f3efac1adc329bb609d0c226572e7f9f3cd3f8"
        },
        "date": 1588761889636,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 70.69908277193706,
            "unit": "sec",
            "range": "stddev: 0.6254807772679284",
            "extra": "mean: 70.69908277193706 sec\nrounds: 3"
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
          "id": "778743d38d4d510cbb81dd91e6ca9130aba187dc",
          "message": "Fix QF",
          "timestamp": "2020-05-06T14:58:17+03:00",
          "tree_id": "b4cc09520f11677e2c589da91e6e4157f975f678",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/778743d38d4d510cbb81dd91e6ca9130aba187dc"
        },
        "date": 1588766890890,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 80.51257904370625,
            "unit": "sec",
            "range": "stddev: 1.6031938985035534",
            "extra": "mean: 80.51257904370625 sec\nrounds: 3"
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
          "id": "52125fd3ae311854328a9c7f1125a3e69bfca040",
          "message": "Оптимизация поиска MethodSymbol в SymbolTree",
          "timestamp": "2020-05-06T17:31:02+03:00",
          "tree_id": "a8690a0de5bb2e5c410babf044b973786f926f60",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/52125fd3ae311854328a9c7f1125a3e69bfca040"
        },
        "date": 1588775864883,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 53.004623810450234,
            "unit": "sec",
            "range": "stddev: 0.845052497535804",
            "extra": "mean: 53.004623810450234 sec\nrounds: 3"
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
          "id": "d864741b6cc186635e40d90c1416e279333ef338",
          "message": "Оптимизация поиска методов",
          "timestamp": "2020-05-06T17:50:25+03:00",
          "tree_id": "d480033a677c9a4606db634cd93c55739ec2bcae",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/d864741b6cc186635e40d90c1416e279333ef338"
        },
        "date": 1588777090824,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 65.64842319488525,
            "unit": "sec",
            "range": "stddev: 1.4135836934312864",
            "extra": "mean: 65.64842319488525 sec\nrounds: 3"
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
          "id": "d3d4c44fe5157f854a3d3656e1abe3f39d2d33bc",
          "message": "Старая реализация case insensetivity в парсере",
          "timestamp": "2020-05-06T17:58:28+03:00",
          "tree_id": "57dd3d3301ed8dd33107d53cd9b6f93c0838fce8",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/d3d4c44fe5157f854a3d3656e1abe3f39d2d33bc"
        },
        "date": 1588777601551,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 67.51455569267273,
            "unit": "sec",
            "range": "stddev: 1.7793590732024618",
            "extra": "mean: 67.51455569267273 sec\nrounds: 3"
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
          "id": "e47bcef0ef0ab2c480ebc095689ad90861ad4122",
          "message": "Merge pull request #1154 from MinimaJack/er-fx-1",
          "timestamp": "2020-05-07T17:12:15+03:00",
          "tree_id": "9eea6d4e8b0c82ca913305b9e95a131e4d93a31c",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e47bcef0ef0ab2c480ebc095689ad90861ad4122"
        },
        "date": 1588861197667,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 62.245399157206215,
            "unit": "sec",
            "range": "stddev: 3.3722874982939386",
            "extra": "mean: 62.245399157206215 sec\nrounds: 3"
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
          "id": "07fbf00bf5a244777df5fcadbcdaa1a932338a75",
          "message": "Merge pull request #1173 from 1c-syntax/fix/unrechebleCodeEndIf\n\nfix unreacheable preproc_endIf fp",
          "timestamp": "2020-05-08T00:45:10+03:00",
          "tree_id": "bd99ecbe33fb790d3c9bf497c6cff2dd9eeddcb1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/07fbf00bf5a244777df5fcadbcdaa1a932338a75"
        },
        "date": 1588888347623,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 59.961162646611534,
            "unit": "sec",
            "range": "stddev: 0.2854078021739969",
            "extra": "mean: 59.961162646611534 sec\nrounds: 3"
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
          "id": "8c3786489442662fc32270ec83f696f898eec05e",
          "message": "Merge pull request #1174 from 1c-syntax/feature/DocumentLink",
          "timestamp": "2020-05-08T11:33:58+03:00",
          "tree_id": "cb8feab7650afbb8dc7ca25287ec6e0ba62bca25",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/8c3786489442662fc32270ec83f696f898eec05e"
        },
        "date": 1588927288020,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 63.48949694633484,
            "unit": "sec",
            "range": "stddev: 2.6232043158925737",
            "extra": "mean: 63.48949694633484 sec\nrounds: 3"
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
          "id": "e5e92933f0947e3849b9ea22bfdf0c20e6593d0f",
          "message": "Merge pull request #1175 from 1c-syntax/feature/cachedPublic",
          "timestamp": "2020-05-08T12:22:28+03:00",
          "tree_id": "6437b8f59b7a990d6fbd3fe814155707e5e14090",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e5e92933f0947e3849b9ea22bfdf0c20e6593d0f"
        },
        "date": 1588930177117,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 60.82535560925802,
            "unit": "sec",
            "range": "stddev: 1.3332327259236498",
            "extra": "mean: 60.82535560925802 sec\nrounds: 3"
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
          "id": "3f67b0285fb6ca6fefcb315c575b63f31fc80d96",
          "message": "Merge pull request #1180 from MinimaJack/patch-2",
          "timestamp": "2020-05-11T12:31:29+03:00",
          "tree_id": "84ebd1c754e44796bd0477add968af195c86d897",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/3f67b0285fb6ca6fefcb315c575b63f31fc80d96"
        },
        "date": 1589189940200,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 59.751272201538086,
            "unit": "sec",
            "range": "stddev: 1.0101054047757976",
            "extra": "mean: 59.751272201538086 sec\nrounds: 3"
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
          "id": "020b251871788a1fcb8f6bcb738460e1bfa85798",
          "message": "Merge pull request #1176 from 1c-syntax/feature/newTemplate",
          "timestamp": "2020-05-11T21:25:05+03:00",
          "tree_id": "8f05fb6c2cdb3c509f25ac6f7afd8ecdf94cd2df",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/020b251871788a1fcb8f6bcb738460e1bfa85798"
        },
        "date": 1589221985032,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 64.65459116299947,
            "unit": "sec",
            "range": "stddev: 0.19350607111572654",
            "extra": "mean: 64.65459116299947 sec\nrounds: 3"
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
          "id": "988b7e752387d0d038c244851223193d32c3224c",
          "message": "Merge pull request #1155 from artbear/safe-mode-fix\n\nДоработка диагностики Ошибка неявного преобразования БезопасныйРежим",
          "timestamp": "2020-05-11T21:29:47+03:00",
          "tree_id": "186ed38ac3dc062407b9dd1054c13414f9bfc4a9",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/988b7e752387d0d038c244851223193d32c3224c"
        },
        "date": 1589222277866,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 71.26645747820537,
            "unit": "sec",
            "range": "stddev: 0.2912828721117854",
            "extra": "mean: 71.26645747820537 sec\nrounds: 3"
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
          "id": "20fcb8953efcf4527fb310cc67b2a71f0baf3c4b",
          "message": "Merge branch 'feature/self-hosted-runner' into develop",
          "timestamp": "2020-05-11T23:44:36+03:00",
          "tree_id": "9dc0b032b8177dbf02abfbab498fff9c2b18d677",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/20fcb8953efcf4527fb310cc67b2a71f0baf3c4b"
        },
        "date": 1589230220992,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.025840044021606,
            "unit": "sec",
            "range": "stddev: 1.077636962331521",
            "extra": "mean: 50.025840044021606 sec\nrounds: 3"
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
          "id": "075b484e613eb117468b8cae44913d531569f427",
          "message": "Исправление поведения EXCEPT\n\nВне зависимости от включения/выключения диагностики, если mode = except, диагностика должна исключаться",
          "timestamp": "2020-05-11T23:44:04+03:00",
          "tree_id": "a39e0f1f277ca22d576e86ad531b3fefab29da80",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/075b484e613eb117468b8cae44913d531569f427"
        },
        "date": 1589230262067,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 52.55107402801514,
            "unit": "sec",
            "range": "stddev: 2.0861845546357842",
            "extra": "mean: 52.55107402801514 sec\nrounds: 3"
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
          "id": "80240be5c7691bcaade3eae275e53367c6abfd2d",
          "message": "Merge pull request #1179 from 1c-syntax/feature/double-parsing\n\nДвойной проход файлов для наполнения базы контекстов",
          "timestamp": "2020-05-12T00:34:22+03:00",
          "tree_id": "d13d1e8117d3b11989200aa0f13eb79f73c83211",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/80240be5c7691bcaade3eae275e53367c6abfd2d"
        },
        "date": 1589233199912,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 52.05723428726196,
            "unit": "sec",
            "range": "stddev: 0.9290600497281724",
            "extra": "mean: 52.05723428726196 sec\nrounds: 3"
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
          "id": "9eafe043729c9a70e3267ce70078dfaf7f962c2a",
          "message": "Merge pull request #1182 from 1c-syntax/fix/LostSemicolonPerfomance",
          "timestamp": "2020-05-12T11:00:12+03:00",
          "tree_id": "e0682d054e90aff4518c1a4fe21a01cbd3496419",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/9eafe043729c9a70e3267ce70078dfaf7f962c2a"
        },
        "date": 1589270747175,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.21088695526123,
            "unit": "sec",
            "range": "stddev: 0.8091087453985",
            "extra": "mean: 50.21088695526123 sec\nrounds: 3"
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
          "id": "1ab65bcb040fa45b88ff240229ae4782ca8b3ca7",
          "message": "Merge pull request #1183 from 1c-syntax/fix/FunctionShouldHaveReturnPerfomance",
          "timestamp": "2020-05-12T11:42:49+03:00",
          "tree_id": "e59a4b1cbc373f77db43c745245cf56ade9d78f1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/1ab65bcb040fa45b88ff240229ae4782ca8b3ca7"
        },
        "date": 1589273307967,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.82437221209208,
            "unit": "sec",
            "range": "stddev: 1.0208799601763956",
            "extra": "mean: 50.82437221209208 sec\nrounds: 3"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "int-it@yandex.ru",
            "name": "Alexey Sosnoviy",
            "username": "asosnoviy"
          },
          "committer": {
            "email": "int-it@yandex.ru",
            "name": "Alexey Sosnoviy",
            "username": "asosnoviy"
          },
          "distinct": true,
          "id": "88c042e12979e2167e70863046aa12b7ae202c38",
          "message": "docs link fix",
          "timestamp": "2020-05-12T18:25:20+03:00",
          "tree_id": "60aaa1fc8ef9c6bd7975eda18bad8975ad6a3979",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/88c042e12979e2167e70863046aa12b7ae202c38"
        },
        "date": 1589297458519,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.8111769358317,
            "unit": "sec",
            "range": "stddev: 1.2255246821344656",
            "extra": "mean: 50.8111769358317 sec\nrounds: 3"
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
          "id": "20c771e59170e1fa608596bedec7dec934a0eb09",
          "message": "Merge pull request #1184 from 1c-syntax/fix/methodSizePerf\n\nMethodSize miniopt",
          "timestamp": "2020-05-13T01:02:37+03:00",
          "tree_id": "468ba64c204f512b9ed053ef8a722503a284d5dd",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/20c771e59170e1fa608596bedec7dec934a0eb09"
        },
        "date": 1589321291843,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 51.6755842367808,
            "unit": "sec",
            "range": "stddev: 0.5060102616667135",
            "extra": "mean: 51.6755842367808 sec\nrounds: 3"
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
          "id": "ce001095437cf8152cd8413b9e53e2646a99a50f",
          "message": "Merge pull request #1185 from 1c-syntax/feature/CommonModuleNameWordsDiagnostic\n\nCommonModuleNameWordsDiagnostic",
          "timestamp": "2020-05-13T14:02:44+03:00",
          "tree_id": "551ccd31cb07006ff489373efdc25bb204ea7249",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/ce001095437cf8152cd8413b9e53e2646a99a50f"
        },
        "date": 1589368121315,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.49314498901367,
            "unit": "sec",
            "range": "stddev: 1.2742506038429684",
            "extra": "mean: 50.49314498901367 sec\nrounds: 3"
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
          "id": "7bee04ab80a4122e6df170599e1d4035ed8138e8",
          "message": "Merge pull request #1157 from 1c-syntax/feature/addMdoRef\n\nДобавил ссылки на метаданные mdoRef",
          "timestamp": "2020-05-13T14:33:31+03:00",
          "tree_id": "ce101b3cd29f01f6129d59ab29edcb099b81c4c1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/7bee04ab80a4122e6df170599e1d4035ed8138e8"
        },
        "date": 1589370520452,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.30738147099813,
            "unit": "sec",
            "range": "stddev: 1.0301878724572548",
            "extra": "mean: 50.30738147099813 sec\nrounds: 3"
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
          "id": "60d4ca81e0f1c78565bc9c63b44e9db4da7c9a64",
          "message": "Merge pull request #1187 from 1c-syntax/feature/smart-benchmark-run\n\nЗапуск бенчмарка только на изменении важных файлов проекта",
          "timestamp": "2020-05-13T16:30:03+03:00",
          "tree_id": "096d4a905b3ccde9296d364c80e8292d31fb67c9",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/60d4ca81e0f1c78565bc9c63b44e9db4da7c9a64"
        },
        "date": 1589377100066,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.37978013356527,
            "unit": "sec",
            "range": "stddev: 1.5264551690651613",
            "extra": "mean: 50.37978013356527 sec\nrounds: 3"
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
          "id": "b8f7337bdf38530b1723ca6f5705c4fe22bf3570",
          "message": "Merge pull request #1192 from 1c-syntax/feature/566\n\nКорректировка поведения UsingModalWindows и UsingSynchronousCalls",
          "timestamp": "2020-05-14T11:32:21+03:00",
          "tree_id": "d2644766c62a676f075dbd8d75d28e39a069c238",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/b8f7337bdf38530b1723ca6f5705c4fe22bf3570"
        },
        "date": 1589445472573,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.890660683314,
            "unit": "sec",
            "range": "stddev: 1.2755021289941508",
            "extra": "mean: 50.890660683314 sec\nrounds: 3"
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
          "id": "130f8f7c33801e13ad8f8e8a6d7f1a4dbc3045c3",
          "message": "Merge pull request #1188 from 1c-syntax/feature/CommonModuleType\n\nCommonModuleInvalidTypeDiagnostic",
          "timestamp": "2020-05-14T11:34:12+03:00",
          "tree_id": "f9aa6faee8db4fb41d8bdc290c59f185af340ef2",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/130f8f7c33801e13ad8f8e8a6d7f1a4dbc3045c3"
        },
        "date": 1589445812155,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 51.04928962389628,
            "unit": "sec",
            "range": "stddev: 1.2468550346886287",
            "extra": "mean: 51.04928962389628 sec\nrounds: 3"
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
          "id": "af408892ac13ff0d091fc1dcadaeeff9baea9f16",
          "message": "Merge pull request #1193 from 1c-syntax/feature/executeExternalCode\n\nНовые диагностики выполнения внешнего кода",
          "timestamp": "2020-05-14T15:11:28+03:00",
          "tree_id": "425dc42eca039316510da21bc40467b1aa657db1",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/af408892ac13ff0d091fc1dcadaeeff9baea9f16"
        },
        "date": 1589458625153,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 50.77826809883118,
            "unit": "sec",
            "range": "stddev: 1.3603566935318194",
            "extra": "mean: 50.77826809883118 sec\nrounds: 3"
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
          "id": "b8c6354b6cbe21c25e0519a50a651949a66d39c7",
          "message": "Исправление чтения частично заданной конфигурации",
          "timestamp": "2020-05-15T09:59:37+03:00",
          "tree_id": "2215cd4f2cc82b8f36cfa7ca1ecfc508ce6155fe",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/b8c6354b6cbe21c25e0519a50a651949a66d39c7"
        },
        "date": 1589526780670,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 52.206092754999794,
            "unit": "sec",
            "range": "stddev: 4.785967909896058",
            "extra": "mean: 52.206092754999794 sec\nrounds: 3"
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
          "id": "e255a76eb9cf36ebd6160fcc712b964867489120",
          "message": "Merge pull request #1196 from 1c-syntax/feature/documentLinkOptions",
          "timestamp": "2020-05-15T15:40:27+03:00",
          "tree_id": "20d2fc65f1fdb3f45cb737b6071a85f771ef1223",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e255a76eb9cf36ebd6160fcc712b964867489120"
        },
        "date": 1589546776326,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 51.32477037111918,
            "unit": "sec",
            "range": "stddev: 0.7584098112351798",
            "extra": "mean: 51.32477037111918 sec\nrounds: 3"
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
          "id": "bbbda213f8c2a9b9b66c8209daa7a88c80c7eb9d",
          "message": "Очистка символьного дерева при перестройке контекста",
          "timestamp": "2020-05-19T13:59:11+03:00",
          "tree_id": "6450d192883fba3232ac6f0a63dd1e7ae751232b",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/bbbda213f8c2a9b9b66c8209daa7a88c80c7eb9d"
        },
        "date": 1589886676808,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 52.93542687098185,
            "unit": "sec",
            "range": "stddev: 2.2480661391371486",
            "extra": "mean: 52.93542687098185 sec\nrounds: 3"
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
          "id": "e3b18aeced1c8e27ee61c55573973d1f02b328af",
          "message": "Merge pull request #1203 from 1c-syntax/feature/fixsonar2005",
          "timestamp": "2020-05-20T18:10:35+03:00",
          "tree_id": "b7bb2916ac7774ae540dc3212dce3a5a246c6564",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/e3b18aeced1c8e27ee61c55573973d1f02b328af"
        },
        "date": 1589987885620,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 51.847113370895386,
            "unit": "sec",
            "range": "stddev: 0.7071283877653733",
            "extra": "mean: 51.847113370895386 sec\nrounds: 3"
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
          "id": "8a936d6174936820e45c3d8fae642fa9d445be5d",
          "message": "Merge pull request #1178 from 1c-syntax/feature/deprecated-diagnostic",
          "timestamp": "2020-05-21T16:35:40+03:00",
          "tree_id": "0322eebb0eb77078fe1fce398d3a6f27e2b446df",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/8a936d6174936820e45c3d8fae642fa9d445be5d"
        },
        "date": 1590068496604,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 54.663489739100136,
            "unit": "sec",
            "range": "stddev: 0.9768306244249583",
            "extra": "mean: 54.663489739100136 sec\nrounds: 3"
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
          "id": "1dd38c86162e4094188b5db7991f99befedac7ee",
          "message": "Merge pull request #1204 from 1c-syntax/feature/up-md\n\nUp mdclasses",
          "timestamp": "2020-05-21T20:16:54+03:00",
          "tree_id": "444c03477853fe43b288d5341a966727d7895fc8",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/1dd38c86162e4094188b5db7991f99befedac7ee"
        },
        "date": 1590081841780,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 54.28012228012085,
            "unit": "sec",
            "range": "stddev: 2.225705868511124",
            "extra": "mean: 54.28012228012085 sec\nrounds: 3"
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
          "id": "050ef2113bb59f15245e14bb741da38c8ee0c6f3",
          "message": "Обновление парсера",
          "timestamp": "2020-05-22T11:50:44+03:00",
          "tree_id": "b7e0718c352f3bbac4df088de70537415e7eac62",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/050ef2113bb59f15245e14bb741da38c8ee0c6f3"
        },
        "date": 1590137847502,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 60.264328718185425,
            "unit": "sec",
            "range": "stddev: 2.1687697827235715",
            "extra": "mean: 60.264328718185425 sec\nrounds: 3"
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
          "id": "06dde9b8a16249534a61a083048f819d1898ded1",
          "message": "Merge pull request #1209 from 1c-syntax/feature/review-visitSomething",
          "timestamp": "2020-05-25T12:39:31+03:00",
          "tree_id": "0f2fed1841ac8e08fa39788499a09904bdb5ea02",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/06dde9b8a16249534a61a083048f819d1898ded1"
        },
        "date": 1590399951662,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 60.239313999811806,
            "unit": "sec",
            "range": "stddev: 0.6973933888488122",
            "extra": "mean: 60.239313999811806 sec\nrounds: 3"
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
          "id": "b8767f2170593506ccc27f898fe4bd5115e927cc",
          "message": "Merge pull request #1219 from 1c-syntax/feature/diagnostic-code-in-document-link",
          "timestamp": "2020-05-28T12:08:50+03:00",
          "tree_id": "3a488dc160223cd8b5a075bde5e25e8a3a8f2893",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/b8767f2170593506ccc27f898fe4bd5115e927cc"
        },
        "date": 1590657359481,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 61.20127240816752,
            "unit": "sec",
            "range": "stddev: 1.0593159324448984",
            "extra": "mean: 61.20127240816752 sec\nrounds: 3"
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
          "id": "008f043bf4afb66f52a5fbd061f946a59149b43e",
          "message": "Update build.gradle.kts",
          "timestamp": "2020-05-28T12:13:51+03:00",
          "tree_id": "9539adc660b23e011a19f45dc16c85e95a4d6c49",
          "url": "https://github.com/1c-syntax/bsl-language-server/commit/008f043bf4afb66f52a5fbd061f946a59149b43e"
        },
        "date": 1590657782091,
        "tool": "pytest",
        "benches": [
          {
            "name": ".github/scripts/benchmark.py::test_analyze_ssl31",
            "value": 61.2011604309082,
            "unit": "sec",
            "range": "stddev: 1.3062578222682009",
            "extra": "mean: 61.2011604309082 sec\nrounds: 3"
          }
        ]
      }
    ]
  }
}