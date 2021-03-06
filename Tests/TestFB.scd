Alga.boot;

(
b = AlgaNode({SinOsc.ar(134 + \fm.ar(0))}, connectionTime:2);
a = AlgaNode({SinOsc.ar(220 + \fm.ar(0))}, connectionTime:3);
c = AlgaNode({\in.ar(0).range(-100, 100)}, connectionTime:2);
d = AlgaNode({\in.ar(0).range(-50, 50)}, connectionTime:4);

o = AlgaNode({\in.ar(0).tanh.dup}, connectionTime:3, playTime:3).play;

c << b;
a <<.fm c;

d << a;
b <<.fm d;

a >> o;
)

(
//Changes FT when replacing c / d
b.ct = 3;
a.ct = 5;

//Changes FT when replacing a / b
c.ct = 3;
d.ct = 4;
)

d.replace({\in.ar(0).range(-100.12312, 10.23)})

c.ct = 3;

a.ct = 5

c.replace({\in.ar(0).range(-30, rrand(10, 10))});

(
fork {
	loop {
		c.replace({\in.ar(0).range(-30, rrand(10, 100))});
		3.wait;
	}
}
)

b.ct_(1).replace({SinOsc.ar(1 + \fm.ar(0))})

a.replace({SinOsc.ar(1 + \fm.ar(0))})

b.replace({Saw.ar(1 + \fm.ar(0))})

b.replace({Gendy1.ar(minfreq: 100 - \fm.ar(0), maxfreq: 100)})

s.scope

~delay = AlgaNode({OmniDelay2.ar(\in.ar, \dt.ar(0.3), \fb.kr(0.9), \dp.kr(0.8), 1).dup.tanh * 0.3})

~delay.play

a >> ~delay

~delay.ct = 4

~delay <<.dt 0.1

~delay <<.dp 0.8

~dt = AlgaNode({DC.ar(0.1)})

~dt.replace({LFNoise0.ar(1).range(0.001, 1).lag(0.1)})

~delay <<.dt ~dt

c << b

b.clear(2)

a.replace({SinOsc.ar(100 + \fm.ar(0))})

~delay.clear(2)

a.clear(2)

c.clear

d.clear

o.clear