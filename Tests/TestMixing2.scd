Alga.boot({s.plotTree})

(
AlgaStartup.algaMaxIO=3;
Alga.initSynthDefs;
)

(
Alga.boot({
	AlgaSynthDef(\mixCounter, {
		DC.kr(1);
	}).add;

	s.sync;

	//s.plotTree;
	a = AlgaNode({SinOsc.ar(\f.kr(440)).dup * 0.2}, connectionTime:1).play;
	b = AlgaNode({DC.kr(220)});
	c = AlgaNode({DC.kr(100)});
	d = AlgaNode({DC.kr(10)});

	a <<.f b;
	a <<+.f c;

	a <<+.f b;//problem here!

	("a" + a.name).postln;
	("b" + b.name).postln;
	("c" + c.name).postln;
})
)

a <<.f b;

a <<+.f b;

a.replaceMix(\f, c, d);

a.from(b, \f, \out3);

a.inNodes
b.outNodes
c.outNodes

a.disconnect(\f, c)


d.replace({DC.kr(5)})

a.disconnect(\f, d);

b.outNodes
c.outNodes
d.outNodes

b.replace({LFNoise0.kr(1).range(400,1000)})

a <| \f

a.debug;

s.plotTree

a.play

(
a.ct=1;
a <<.f 323;
)

a.debug
s.scope