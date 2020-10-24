//This class is probably useless: the difference between .ar and .kr is not anything relevant,
//I could just use the standard EnvGate (which uses .kr method)
AlgaEnvGate {
  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business
	*ar { | i_level=0, gate, fadeTime, doneAction=2, curve=\sin |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.ar(
			Env.new([i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business
	*kr { | i_level=0, gate, fadeTime, doneAction=2, curve=\sin |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.kr(
			Env.new([i_level, 1.0, 0.0], #[1.0, 1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}
}

AlgaEnvGateInv {
  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business
	*ar { | i_level=1, gate, fadeTime, doneAction=2, curve=\sin |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.ar(
			Env.new([ i_level, 0.0], #[1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}

  //Don't give default values to gate and fadeTime, or it would screw the namedcontrol business
	*kr { | i_level=1, gate, fadeTime, doneAction=2, curve=\sin |
		var synthGate = gate ?? { NamedControl.kr(\gate, 1.0) }; //This retrieves \gate from upper AlgaSynthDef
		var synthFadeTime = fadeTime ?? { NamedControl.kr(\fadeTime, 0) }; //This retrieves \fadeTime from upper AlgaSynthDef
		^EnvGen.kr(
			Env.new([ i_level, 0.0], #[1.0], curve, 1),
			synthGate, 1.0, 0.0, synthFadeTime, doneAction
		)
	}
}