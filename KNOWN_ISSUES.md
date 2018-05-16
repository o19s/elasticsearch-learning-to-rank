# Known issues

## Cache deadlock

All elasticsearch versions between 5.5.3 (included) and 6.3.0 (excluded) are affected by a [bug](https://github.com/elastic/elasticsearch/pull/30461) in the internal cache that may lead to a serious deadlock that could in theory leak all the search threads.
A possible workaround is to configure the cache to avoid as much as possible its eviction mechanism by setting these options in your `elasticsearch.yml` file:

```
ltr.caches.expire_after_write: 0
ltr.caches.expire_after_read: 0
ltr.caches.max_mem: 100mb
```

Elasticsearch versions 5.6.10, 6.3.0 and onwards should include the bugfix for this bug.

