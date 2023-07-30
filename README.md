
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
Capture trafick: tcpdump -w websocket.pcap -s 2500 -vv -i lo


https://medium.com/@pawilon/tuning-your-linux-kernel-and-haproxy-instance-for-high-loads-1a2105ea553e
https://www.linangran.com/?p=547

sipp -sf uac-auth.xml  0.0.0.0:5068 -t tn -max_socket 20 -trace_err

RFC:

https://www.rfc-editor.org/rfc/rfc5626
https://datatracker.ietf.org/doc/html/rfc3327
https://datatracker.ietf.org/doc/html/rfc7118