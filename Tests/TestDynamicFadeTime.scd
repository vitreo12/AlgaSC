(
AlgaStartup.algaMaxIO = 2;
Alga.initSynthDefs;
Alga.boot({s.plotTree});
)

a = AlgaNode({SinOsc.ar(\f.kr(440))}).play

a.from(1000, \f).ct_(5)
a.from(100, \f).ct_(1);

//Now you can do this

a.from(1000, \f, time:5);
a.from(100, \f, time:1);

s.scope