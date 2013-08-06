var draw_bar_graph = function(data) {
    sorted  = data.stats.sort(function(a,b){return a.amount - b.amount;});
    sorted = sorted.reverse();
    data = $(sorted).slice(0, 100);
    //maximum of data you want to use
    var content = d3.select("#content"),
        data_max = d3.max(data).amount,
        //number of tickmarks to use
        num_ticks = 10,
        //margins
        left_margin = 330,
        right_margin = 330,
        top_margin = 30,
        bottom_margin = 0;

    content.insert("h2", ".bar-chart").text("Bar Graph");
    content.insert("h3", ".bar-chart").text("Top 20 API Request");

    var w = 1050,                        //width
        h = 2000,                        //height
        color = function(id) { return '#00b3dc';};

    var x = d3.scale.linear()
        .domain([0, data_max])
        .range([0, w - (left_margin + right_margin) ]),
        y = d3.scale.ordinal()
        .domain(d3.range(data.length))
        .rangeBands([bottom_margin, h - top_margin], 0.5);


    var chart_top = h - y.rangeBand()/2 - top_margin;
    var chart_bottom = bottom_margin + y.rangeBand()/2;
    var chart_left = left_margin + 100;
    var chart_right = w - 100;

    /*
     *  Setup the SVG element and position it
     */
    var vis = content
        .append("svg:svg")
            .attr("width", w)
            .attr("height", h)
        .append("svg:g")
            .attr("id", "barchart")
            .attr("class", "barchart");


    //Ticks
    var rules = vis.selectAll("g.rule")
        .data(x.ticks(num_ticks))
    .enter()
        .append("svg:g")
        .attr("transform", function(d)
                {
                return "translate(" + (chart_left + x(d)) + ")";});
    rules.append("svg:line")
        .attr("class", "tick")
        .attr("y1", chart_top)
        .attr("y2", chart_top + 4)
        .attr("stroke", "black");

    rules.append("svg:text")
        .attr("class", "tick_label")
        .attr("text-anchor", "middle")
        .attr("y", chart_top)
        .text(function(d)
        {
        return d;
        });
    var bbox = vis.selectAll(".tick_label").node().getBBox();
    vis.selectAll(".tick_label")
    .attr("transform", function(d)
            {
            return "translate(0," + (bbox.height) + ")";
            });

    var bars = vis.selectAll("g.bar")
        .data(data)
    .enter()
        .append("svg:g")
            .attr("class", "bar")
            .attr("transform", function(d, i) {
                    return "translate(0, " + y(i) + ")";
            });

    bars.append("svg:rect")
        .attr("x", chart_left)
        .attr("width", function(d) {
                return (x(d.amount));
                })
        .attr("height", y.rangeBand())
        .attr("fill", color(0))
        .attr("stroke", color(0));


    //Labels
    var labels = vis.selectAll("g.bar")
        .append("svg:a").attr("width", 200).append("svg:text")
            .attr("class", "label")
            .attr("x", 20)
            .attr("text-anchor", "right")
            .text(function(d) {
                    return d.url;
                    });

    bbox = labels.node().getBBox();
    vis.selectAll(".label")
        .attr("transform", function(d) {
                return "translate(0, " + (y.rangeBand()/2 + bbox.height / 4 ) + ")";
                });


    labels = vis.selectAll("g.bar")
        .append("svg:text")
            .attr("class", "value")
            .attr("x", function(d) {
                return x(d.amount) + chart_left + 10;
            })
            .attr("text-anchor", "left")
            .text(function(d) {
                return "" + d.amount;
            });

    bbox = labels.node().getBBox();
    vis.selectAll(".value")
        .attr("transform", function(d) {
            return "translate(0, " + (y.rangeBand()/2 + bbox.height/4) + ")";
        });

    //Axes
    vis.append("svg:line")
        .attr("class", "axes")
        .attr("x1", chart_left)
        .attr("x2", chart_left)
        .attr("y1", chart_bottom)
        .attr("y2", chart_top)
        .attr("stroke", "black");
     vis.append("svg:line")
        .attr("class", "axes")
        .attr("x1", chart_left)
        .attr("x2", chart_right)
        .attr("y1", chart_top)
        .attr("y2", chart_top)
        .attr("stroke", "black");
};


d3.json("obp/metrics/demo-bar", function (data) {
    draw_bar_graph(data);
});


d3.json("obp/metrics/demo-line",function(data) {

  // helper function
  function getDate(d) {
      return new Date(d.date);
  }

  // get max and min dates - this assumes data is sorted
  var minDate = getDate(data.stats[0]),
      maxDate = getDate(data.stats[data.stats.length-1]);

 var height_cord = Math.max.apply(Math, data.stats.map(function(v) {
                    return v.amount;
                }));

  var w = 990,
  h = 450,
  p = 30,
  margin = 20,
  y = d3.scale.linear().domain([height_cord, 0]).range([0 + margin, h - margin]),
  x = d3.time.scale().domain([minDate, maxDate]).range([0 + margin, w - margin]);

  var vis = d3.select("#content")
  .data([data.stats])
  .append("svg:svg")
  .attr("width", w + p * 2)
  .attr("height", h + p * 2)
  .attr("class", "line-chart")
  .append("svg:g")
  .attr("transform", "translate(" + p + "," + p + ")");

  var rules = vis.selectAll("g.rule")
  .data(x.ticks(3))
  .enter().append("svg:g")
  .attr("class", "rule");

  rules.append("svg:line")
  .attr("x1", x)
  .attr("x2", x)
  .attr("y1", 0)
  .attr("y2", h - 1);

  rules.append("svg:line")
  .attr("class", function(d) { return d ? null : "axis"; })
  .attr("y1", y)
  .attr("y2", y)
  .attr("x1", 0)
  .attr("x2", w + 1);

  rules.append("svg:text")
  .attr("x", x)
  .attr("y", h + 3)
  .attr("dy", ".71em")
  .attr("text-anchor", "middle")
  .text(x.tickFormat(3));

  rules.append("svg:text")
  .attr("y", y)
  .attr("x", -3)
  .attr("dy", ".35em")
  .attr("text-anchor", "end")
  .text(y.tickFormat(3));

  vis.append("svg:path")
  .attr("class", "line")
  .attr("d", d3.svg.line()
      .x(function(d) { return x(getDate(d)); })
      .y(function(d) { return y(d.amount); })
  );

  vis.selectAll("circle.line")
  .data(data.stats)
  .enter().append("svg:circle")
  .attr("class", "line")
  .attr("cx", function(d) { return x(getDate(d)); })
  .attr("cy", function(d) { return y(d.amount); })
  .attr("r", 3.5);

  var yAxisLeft = d3.svg.axis().scale(y).ticks(5).orient("left");
              // Add the y-axis to the left
              vis.append("svg:g")
                    .attr("class", "y axis")
                    .attr("transform", "translate(10,0)")
                    .call(yAxisLeft);

    var content = d3.select("#content");
    content.insert("h2", ".line-chart").text("Line Graph");
    content.insert("h3", ".line-chart").text("Daily API Request");
    content.insert("p", ".line-chart").attr("class", "axis").text("Request");
});
