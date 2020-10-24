(
SynthDef(\snd, {
	Out.kr(\out.ir(0), DC.kr(0.5) * \env.kr(0))
}).add;

SynthDef(\env, {
	var env = AlgaEnvGate.kr(doneAction: Done.freeGroup);
	Out.kr(\out.ir(0), env);
}).add;

SynthDef(\envInv, {
	var env = AlgaEnvGateInv.kr(
		i_level: In.kr(\out.ir(0)), //read starting value from same bus!
		doneAction: Done.freeGroup
	);

	//Free prev env right away
	Free.kr(1, \prevEnvID.ir(-1));

	//ReplaceOut is essential here
	ReplaceOut.kr(\out.ir(0), env);
}).add;
)

(
~group = Group(s);
~bus = Bus.control(s, 2);

~sndBus = ~bus.subBus(0);
~envBus = ~bus.subBus(1);

~synth = Synth(\snd, [\out, ~sndBus.index, \env, ~envBus.busArg], ~group);
~env = Synth(\env, [\out, ~envBus.index, \fadeTime, 10], ~group);
~bus.scope;
)

~env.set(\gate, 0, \fadeTime, 10);

~envInv = Synth(\envInv, [\out, ~envBus.index, \prevEnvID, ~env.nodeID, \fadeTime, 5], ~group);

~envInv2 = Synth(\envInv, [\out, ~envBus.index, \prevEnvID, ~envInv.nodeID, \fadeTime, 0.1], ~group);
