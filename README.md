# bss-cloud-pipeline-lib

Repozytorium z zasobami wykorzystywanymi jako **Global Pipeline Library** w
Jenkins do tworzenia pipeline`ów.

# Testowanie

## Testy elementów składowych pipeline

Testy napisane w [bats](https://github.com/bats-core/bats-core) opisane są w [test/bats/]. Aby je uruchomić należy użyć bats z nazwą katalogu:

```
bats test/bats/
```

## Struktura Shared Library w Jenkins Pipeline 

Shared Library w Jenkins Pipeline mają specyficzną (z punktu widzenia programisty Java) strukturę:

```
(root)
+- src                     # Groovy source files
|   +- org
|       +- foo
|           +- Bar.groovy  # for org.foo.Bar class
+- vars
|   +- foo.groovy          # for global 'foo' variable
|   +- foo.txt             # help for 'foo' variable
+- resources               # resource files (external libraries only)
|   +- org
|       +- foo
|           +- bar.json    # static helper data for org.foo.Bar
```

Nasz kod dodajemy albo jako zmienne globalne w `/vars` albo w `/src`.

Strktura zaczęrpnięta z [oficjalnej dokumentacji](https://jenkins.io/doc/book/pipeline/shared-libraries/),
gdzie można znaleźć więcej szcegółów implementacyjnych.
# bss-cloud-pipeline-lib-ailleron
