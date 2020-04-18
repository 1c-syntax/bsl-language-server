import json
from pybadges import badge

value = '0'
with open("output.json", "r") as file:
  data = json.load(file)
  total = data.get('benchmarks')[0].get('stats').get('mean')
  value = '{:.2f}'.format(total)

svg = badge(left_text='Benchmark (SSL 1.0)', right_text=value)
f = open('benchmark.svg', 'w')
f.write(svg)
f.close()
