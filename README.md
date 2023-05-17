
Run load test
```shell
cat load-test.js | docker run --net=host --rm -i grafana/k6 run -
```

Run load test with metrics exported to Prometheus:
```shell
cat load-test.js | docker run -e "K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true" -e "K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write" --net=host --rm -i grafana/k6 run -o experimental-prometheus-rw -
```

Jaeger: http://localhost:16686

Kibana: http://localhost:5601/app/home

Grafana: http://localhost:3000

Prometheus: http://localhost:9090


curl -XPUT -H "Content-Type: application/json" http://localhost:9200/_cluster/settings -d '{ "transient": { "cluster.routing.allocation.disk.threshold_enabled": false } }'
curl -XPUT -H "Content-Type: application/json" http://localhost:9200/_all/_settings -d '{"index.blocks.read_only_allow_delete": null}' 