(
AlgaStartup.algaMaxIO=2;
Alga.initSynthDefs;

AlgaScheduler.verbose = true;

Alga.boot({

	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.ar([440, 441]) + \something.kr(1)) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	AlgaSynthDef(\test2, {
		Saw.ar(\freq.kr(0)) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	s.sync;

	b = AlgaNode({SinOsc.kr(1).range(100, 500)});

	a = AlgaPattern((
		def: \test,
		dur: Pseq([0.25, 0.5, 1]),
		freq: Pwhite(100, 1000)
	)).play;
});
)

a.interpPattern(\freq, Pseq([1000, 880]), 2)

a.interpPattern(\freq, Pwhite(1000, 800), 2)

a.interpPattern(\dur, Pwhite(0.01, 0.3), 10)



b = AlgaNode({SinOsc.ar.range(100, 2000)})

a.interpPattern(\freq, Pseq([b]), 5)

(
Pbind(
	\instrument, \test,
	\dur, Pseq([0.25, 0.5, 1]),
	\freq, Pwhite(200, 1000).dup
).play
)

//This sounds so weird: it's doubling!
(
Pbind(
	\instrument, \test2,
	\dur, Pseq([0.25, 0.5, 1]),
	\freq, Pwhite([300, 200], [1000, 2000])
).play
)

(
a = AlgaPattern((
	def: \test,
	dur: Pseq([0.25, 0.5, 1]),
	freq: Pwhite(300, 1000)
))
)

a <<.freq Pseq([440, 880])

a <<.dur Pwhite(0.01, 1);

a.replace(\test2);

(
a.replace((
	def: \test,
	dur: Pseq([0.01, 0.2])
));
)



(
Alga.boot({
	AlgaSynthDef(\test, {
		SinOsc.ar(\freq.kr(440)) * EnvGen.ar(Env.perc, doneAction:2)
	}).add;

	s.sync;

	AlgaScheduler.verbose = true;

	a = AlgaPattern((
		synth: \test
	));

	c = Pbind(
		\freq, Pwhite([200, 400], 300),
		\dur, Pseq([0.5, 1]),
		\instrument, \test,
		\type, \algaNote,
		\algaPattern, a
	);

	c.play;
})
)

c.patternpairs.postln