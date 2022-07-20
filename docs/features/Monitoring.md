# Мониторинг и отправка ошибок

BSL Language Server содержит механизм отправки данных об ошибках в Sentry.

В качестве сервера Sentry используется публичный Sentry.io по адресу 
https://sentry.io/organizations/1c-syntax/projects/bsl-language-server

К сожалению, особенности настройки доступа к Sentry.io ограничивают возможность просмотра данных
неавторизованным пользователям.

## Схема работы

В [конфигурационном файле](ConfigurationFile.md) BSL Language Server можно настроить режим отправки сообщений об ошибках.
Режим отправки может принимать одно из трех значений:

* спросить (по умолчанию);
* отправлять всегда;
* никогда не отправлять.

Если в конфигурационном файле отсутствует настройка или она имеет значение "спросить", при возникновении ошибки
подключенному language client (используемой IDE) отправляется вопрос о дальнейших действиях с ошибкой
со следующими вариантами ответа:

* отправить эту ошибку, но спросить снова;
* не отправлять эту ошибку, но спросить снова.
* отправить эту ошибку и больше не спрашивать;
* не отправлять эту ошибку и больше не спрашивать;

От ответа можно полностью отказаться (например, нажав на крестик у уведомления с вопросом).
Отсутствие ответа воспринимается как "не отправлять эту ошибку, но спросить снова".

Сообщение об ошибке не содержит идентификационных данных пользователя или рабочей станции, 
за исключением генерируемого UUID сессии для связывания ошибок, возникающих в течении одного
запуска BSL Language Server.

> Обратите внимание!

Если вы включите отладочные логи (настроив `logback` или используя переменные среды), содержимое логов (последние 100 записей) приложится к отправляемому событию.

Некоторые сообщения, пересылаемые между Language Client и BSL Language Server, содержат фрагменты исходного кода
или текст файла целиком.
Эти фрагменты тоже могут быть приложены к отправляемому сообщению.

Пример отправляемого события об ошибке:

??? event.json

    ```json
    {
        "event_id": "746e2e82f4c1499abcdd935bc4c26644",
        "project": 5790531,
        "release": "ae081de9d3c3496ddac1d176259365191966b0cd",
        "dist": null,
        "platform": "java",
        "message": "Internal error: java.lang.RuntimeException: psss",
        "datetime": "2022-07-14T11:07:57.875000Z",
        "tags": [
            [
                "environment",
                "production"
            ],
            [
                "level",
                "error"
            ],
            [
                "logger",
                "org.eclipse.lsp4j.jsonrpc.RemoteEndpoint"
            ],
            [
                "runtime",
                "AdoptOpenJDK 15.0.2"
            ],
            [
                "runtime.name",
                "AdoptOpenJDK"
            ],
            [
                "release",
                "ae081de9d3c3496ddac1d176259365191966b0cd"
            ],
            [
                "user",
                "id:49516eb9-2a0d-4a15-bd96-978b68d8d0df"
            ],
            [
                "server.version",
                "feature-sentry-ae081de-DIRTY"
            ]
        ],
        "_metrics": {
            "bytes.ingested.event": 3289,
            "bytes.stored.event": 9875
        },
        "contexts": {
            "runtime": {
                "name": "AdoptOpenJDK",
                "version": "15.0.2",
                "type": "runtime"
            }
        },
        "culprit": "java.util.concurrent.CompletableFuture in encodeThrowable",
        "environment": "production",
        "exception": {
            "values": [
                {
                    "type": "RuntimeException",
                    "value": "psss",
                    "module": "java.lang",
                    "stacktrace": {
                        "frames": [
                            {
                                "function": "run",
                                "module": "java.util.concurrent.ForkJoinWorkerThread",
                                "in_app": false
                            },
                            {
                                "function": "runWorker",
                                "module": "java.util.concurrent.ForkJoinPool",
                                "in_app": false
                            },
                            {
                                "function": "scan",
                                "module": "java.util.concurrent.ForkJoinPool",
                                "in_app": false
                            },
                            {
                                "function": "topLevelExec",
                                "module": "java.util.concurrent.ForkJoinPool$WorkQueue",
                                "in_app": false
                            },
                            {
                                "function": "doExec",
                                "module": "java.util.concurrent.ForkJoinTask",
                                "in_app": false
                            },
                            {
                                "function": "exec",
                                "module": "java.util.concurrent.CompletableFuture$AsyncSupply",
                                "in_app": false
                            },
                            {
                                "function": "run",
                                "module": "java.util.concurrent.CompletableFuture$AsyncSupply",
                                "in_app": false
                            },
                            {
                                "function": "lambda$prepareCallHierarchy$8",
                                "module": "com.github._1c_syntax.bsl.languageserver.BSLTextDocumentService",
                                "filename": "BSLTextDocumentService.java",
                                "abs_path": "BSLTextDocumentService.java",
                                "lineno": 234,
                                "in_app": true
                            },
                            {
                                "function": "prepareCallHierarchy",
                                "module": "com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider",
                                "filename": "CallHierarchyProvider.java",
                                "abs_path": "CallHierarchyProvider.java",
                                "lineno": 62,
                                "in_app": true
                            }
                        ]
                    },
                    "thread_id": 24
                },
                {
                    "type": "CompletionException",
                    "value": "java.lang.RuntimeException: psss",
                    "module": "java.util.concurrent",
                    "stacktrace": {
                        "frames": [
                            {
                                "function": "run",
                                "module": "java.util.concurrent.ForkJoinWorkerThread",
                                "in_app": false
                            },
                            {
                                "function": "runWorker",
                                "module": "java.util.concurrent.ForkJoinPool",
                                "in_app": false
                            },
                            {
                                "function": "scan",
                                "module": "java.util.concurrent.ForkJoinPool",
                                "in_app": false
                            },
                            {
                                "function": "topLevelExec",
                                "module": "java.util.concurrent.ForkJoinPool$WorkQueue",
                                "in_app": false
                            },
                            {
                                "function": "doExec",
                                "module": "java.util.concurrent.ForkJoinTask",
                                "in_app": false
                            },
                            {
                                "function": "exec",
                                "module": "java.util.concurrent.CompletableFuture$AsyncSupply",
                                "in_app": false
                            },
                            {
                                "function": "run",
                                "module": "java.util.concurrent.CompletableFuture$AsyncSupply",
                                "in_app": false
                            },
                            {
                                "function": "completeThrowable",
                                "module": "java.util.concurrent.CompletableFuture",
                                "in_app": false
                            },
                            {
                                "function": "encodeThrowable",
                                "module": "java.util.concurrent.CompletableFuture",
                                "in_app": false
                            }
                        ]
                    },
                    "thread_id": 24
                }
            ]
        },
        "extra": {
            "thread_name": "ForkJoinPool.commonPool-worker-19"
        },
        "fingerprint": [
            "{{ default }}"
        ],
        "grouping_config": {
            "enhancements": "eJybzDRxY3J-bm5-npWRgaGlroGxrpHxBABcYgcZ",
            "id": "newstyle:2019-10-29"
        },
        "hashes": [
            "4a63b2a190b48dc2bbafb225c5140009",
            "cfeccf65e86c6129d81e7fd1df568ce3"
        ],
        "ingest_path": [
            {
                "version": "22.6.0",
                "public_key": "XE7QiyuNlja9PZ7I9qJlwQotzecWrUIN91BAO7Q5R38"
            }
        ],
        "key_id": "1646438",
        "level": "error",
        "logentry": {
            "formatted": "Internal error: java.lang.RuntimeException: psss"
        },
        "logger": "org.eclipse.lsp4j.jsonrpc.RemoteEndpoint",
        "metadata": {
            "display_title_with_tree_label": false,
            "function": "encodeThrowable",
            "type": "CompletionException",
            "value": "java.lang.RuntimeException: psss"
        },
        "nodestore_insert": 1657796884.036383,
        "received": 1657796882.652077,
        "sdk": {
            "name": "sentry.java.spring-boot",
            "version": "6.2.1",
            "packages": [
                {
                    "name": "maven:io.sentry:sentry",
                    "version": "6.2.1"
                },
                {
                    "name": "maven:io.sentry:sentry-spring-boot-starter",
                    "version": "6.2.1"
                }
            ]
        },
        "timestamp": 1657796877.875,
        "title": "CompletionException: java.lang.RuntimeException: psss",
        "type": "error",
        "user": {
            "id": "49516eb9-2a0d-4a15-bd96-978b68d8d0df"
        },
        "version": "7",
        "location": null
    }
    ```