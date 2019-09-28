# Using hardcode file paths and ip addresses in code

It's forbidden to store in code:
* Paths to files and folders (Windows, Unix)
* Network addresses (ip6, ip4)

## Parameters

* `searchWordsExclusion` - `Строка` - Keywords to exclude expressions when searching for IP addresses. Default
``Верси|Version``.
* `enableSearchNetworkAddresses` - `Булево` - Use network address search. Default ``Yes``.
