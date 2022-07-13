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

* не отправлять эту ошибку и больше не спрашивать;
* отправить эту ошибку и больше не спрашивать;
* отправить эту ошибку, но спросить снова;
* не отправлять эту ошибку, но спросить снова.

От ответа можно полностью отказаться (например, нажав на крестик у уведомления с вопросом).
Отсутствие ответа воспринимается как "не отправлять эту ошибку, но спросить снова".

Пример отправляемого события об ошибке:

??? event.json

    ```json
    {
        "event_id": "16884ea2625448f4b72d440ff2431859",
        "project": 5790531,
        "release": "00c4dbafc46d892196d8ce62ea0b08665dc72b93",
        "dist": null,
        "platform": "java",
        "message": "Internal error: java.lang.RuntimeException: psss",
        "datetime": "2022-07-13T19:34:53.742000Z",
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
                "00c4dbafc46d892196d8ce62ea0b08665dc72b93"
            ],
            [
                "server.version",
                "feature-sentry-00c4dba-DIRTY"
            ]
        ],
        "_metrics": {
            "bytes.ingested.event": 3582,
            "bytes.stored.event": 10144
        },
        "breadcrumbs": {
            "values": [
                {
                    "timestamp": 1657740873.487,
                    "type": "default",
                    "category": "org.eclipse.lsp4j.jsonrpc.RemoteEndpoint",
                    "level": "error",
                    "message": "Internal error: java.lang.RuntimeException: psss"
                },
                {
                    "timestamp": 1657740889.96,
                    "type": "default",
                    "category": "org.eclipse.lsp4j.jsonrpc.RemoteEndpoint",
                    "level": "error",
                    "message": "Internal error: java.lang.RuntimeException: psss"
                }
            ]
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
                                "lineno": 230,
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
                    "thread_id": 28
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
                    "thread_id": 28
                }
            ]
        },
        "extra": {
            "thread_name": "ForkJoinPool.commonPool-worker-27"
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
        "nodestore_insert": 1657740907.677471,
        "received": 1657740905.349647,
        "sdk": {
            "name": "sentry.java.spring-boot",
            "version": "6.2.0",
            "packages": [
                {
                    "name": "maven:io.sentry:sentry",
                    "version": "6.2.0"
                },
                {
                    "name": "maven:io.sentry:sentry-spring-boot-starter",
                    "version": "6.2.0"
                }
            ]
        },
        "timestamp": 1657740893.742,
        "title": "CompletionException: java.lang.RuntimeException: psss",
        "type": "error",
        "version": "7",
        "location": null
    }
    ```