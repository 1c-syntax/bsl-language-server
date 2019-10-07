# Using hardcode file paths and ip addresses in code

It's forbidden to store in code:
* Paths to files and folders (Windows, Unix)
* Network addresses (ip6, ip4)

## Parameters

* `searchWordsExclusion` - `string` - Keywords to exclude expressions when searching for IP addresses. Default
``Верси|Version|ЗапуститьПриложение|RunApp|Пространств|Namespace``.
* `searchWordsStdPathsUnix` - `string` - Keywords to search standard root directory Unix. 
Default ``bin|boot|dev|etc|home|lib|lost\\+found|misc|mnt|media|opt|proc|root|run|sbin|tmp|usr|var``.
* `enableSearchNetworkAddresses` - `boolean` - Use network address search. Default ``Yes``.
