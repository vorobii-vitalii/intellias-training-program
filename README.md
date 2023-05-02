
Run load test
```shell
cat load-test.js | docker run --net=host --rm -i grafana/k6 run -
```

View logs:
<a href="http://localhost:5601/app/home">Kibana URL</a>

Grafana: http://localhost:3000

Prometheus: http://localhost:9090
