(
s.latency = 0.5;
//AlgaScheduler.verbose = true;
Alga.boot({
	AlgaSynthDef(\t, { SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 } ).add;
	AlgaSynthDef(\t_const, { SinOsc.ar(Rand(220, 2000)) * 0.01 } ).add;
	AlgaSynthDef(\bypass, { \in.ar } ).add;
});
)

//This was bugging because of the .sync in dispatchFunction, which screws ordering!
//Now it's fixed by pushing one more action to AlgaScheduler (check implementation)
100.do { AN({ SinOsc.ar(Rand(220, 2000)) * EnvGen.ar(Env.perc) * 0.1 }, sched:1).play };

//This works as expected
100.do { AN(\t, sched:1).play };

//This was bugging because of the .sync in dispatchFunction, which screws ordering!
//Now it's fixed by pushing one more action to AlgaScheduler (check implementation)
(
a = AN({ \in.ar }).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:0.3); };
)

//This works as expected
(
a = AN(\bypass).play;
100.do { |i| b = AN(\t_const); a.mixFrom(b, time:1); };
)