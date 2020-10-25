AlgaStartup {
	classvar <algaMaxIO = 16;

	classvar <algaSynthDefPath;
	classvar <algaSynthDefIOPath;

	*initClass {
		algaSynthDefPath = SynthDef.synthDefDir ++ "AlgaSynthDefs";
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO).asString;
	}

	*algaMaxIO_ { | val |
		if(val.isNumber.not, { "AlgaStartup: algaMaxIO must be a number".error; ^this });
		algaMaxIO = val;
		this.updateAlgaSynthDefIOPath;
	}

	*updateAlgaSynthDefIOPath {
		algaSynthDefIOPath = (algaSynthDefPath ++ "/IO_" ++ algaMaxIO).asString;
	}

	*initSynthDefs {
		var folderDeleted = true;

		if(File.exists(algaSynthDefPath), {
			folderDeleted = File.deleteAll(algaSynthDefPath);
		});

		if(folderDeleted, {
			var algaSynthDefFolderCreated = File.mkdir(algaSynthDefPath);

			if(algaSynthDefFolderCreated, {
				var algaSynthDefIOFolderCreated = File.mkdir(algaSynthDefIOPath);

				if(algaSynthDefIOFolderCreated, {
					"-> Creating all Alga SynthDefs, it may take a while...".postln;
					this.initAlgaPlay;
					this.initAlgaInterp;
					this.initAlgaNorm;
					this.initAlgaMixFades;
					"-> Done!".postln;
				}, {
					("Could not create path: " ++ algaSynthDefIOPath).error;
				});
			}, {
				("Could not create path: " ++ algaSynthDefPath).error;
			});
		}, {
			("Could not delete path: " ++ algaSynthDefPath).error;
		});
	}

	*initAlgaPlay {

		var alreadyDonePairs = IdentityDictionary.new;

		algaMaxIO.do({ | i |
			var arrayOfZeros_in, arrayOfIndices;

			i = i + 1;

			if(i == 1, {
				arrayOfZeros_in = "0";

			}, {
				arrayOfZeros_in = "[";

				//[0, 0, 0...
				i.do({ | num |
					arrayOfZeros_in = arrayOfZeros_in ++ "0,";

				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros_in = arrayOfZeros_in[0..(arrayOfZeros_in.size - 2)] ++ "]";
			});

			algaMaxIO.do({ | y |

				var sdef, arrayOfIndices, currentPair, isAlreadyDone;

				y = y + 1;

				if(y <= i, { //only y <= i
					currentPair = [i, y];
					isAlreadyDone = alreadyDonePairs[currentPair];

					if(isAlreadyDone != true , {
						if(y == 1, {
							arrayOfIndices = "0";
						}, {
							arrayOfIndices = "[";

							y.do({ | num |
								arrayOfIndices = arrayOfIndices ++ num.asString ++ ",";
							});

							arrayOfIndices = arrayOfIndices[0..(arrayOfIndices.size - 2)] ++ "]";
						});

						//Limiter to make sure not to blow up speakers
						sdef = "
AlgaSynthDef(\\alga_play_" ++ i ++ "_" ++ y ++ ", {
var input = \\in.ar(" ++ arrayOfZeros_in ++ ");
input = Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), input);
Out.ar(\\out.ir(0), Limiter.ar(input) * AlgaEnvGate.ar)
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";

						sdef.interpret;
					});
				});
			});
		});
	}

	*initAlgaInterp {

		var alreadyDonePairs = IdentityDictionary.new(algaMaxIO);

		//var file = File("~/AlgaSynthDefsTest.scd".standardizePath,"w");

		//Env
		var envAr = "
AlgaSynthDef(\\alga_env_audio, {  | envCurve = \\sin |
var env = AlgaEnvGate.ar(
curve: envCurve,
doneAction: Done.freeGroup
);
Out.ar(\\out.ir(0), env)
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";
		var envKr = "
AlgaSynthDef(\\alga_env_control, { | envCurve = \\sin |
var env = AlgaEnvGate.kr(
curve: envCurve,
doneAction: Done.freeGroup
);
Out.kr(\\out.ir(0), env)
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";
		var envInvAr = "
AlgaSynthDef(\\alga_envInv_audio, { | envCurve = \\sin |
var env = AlgaEnvGateInv.ar(
i_level: In.ar(\\out.ir(0)), //read starting value from same bus!
curve: envCurve,
doneAction: Done.freeGroup
);

//Free prev env right away
Free.kr(1, \\prevEnvID.ir(-1));

//ReplaceOut is essential here
ReplaceOut.ar(\\out.ir(0), env);
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";
		var envInvKr = "
AlgaSynthDef(\\alga_envInv_control, { | envCurve = \\sin |
var env = AlgaEnvGateInv.kr(
i_level: In.kr(\\out.ir(0)), //read starting value from same bus!
curve: envCurve,
doneAction: Done.freeGroup
);

//Free prev env right away
Free.kr(1, \\prevEnvID.ir(-1));

//ReplaceOut is essential here
ReplaceOut.kr(\\out.ir(0), env);
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);
";
		envAr.interpret;
		envKr.interpret;
		envInvAr.interpret;
		envInvKr.interpret;

		algaMaxIO.do({ | i |

			var arrayOfZeros_in;

			i = i + 1;

			if(i == 1, {
				arrayOfZeros_in = "0";
			}, {
				arrayOfZeros_in = "[";

				//[0, 0, 0...
				i.do({
					arrayOfZeros_in = arrayOfZeros_in ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros_in = arrayOfZeros_in[0..(arrayOfZeros_in.size - 2)] ++ "]";
			});

			algaMaxIO.do({ | y |

				var arrayOfIndices;
				var arrayOfMinusOnes, arrayOfOnes;
				var currentPair, isAlreadyDone;

				y = y + 1;

				if(y == 1, {
					arrayOfIndices = "0";
					arrayOfMinusOnes = "-1.0";
					arrayOfOnes = "1.0";
				}, {
					arrayOfIndices = "[";
					arrayOfMinusOnes = "[";
					arrayOfOnes = "[";

					y.do({ | num |
						arrayOfIndices = arrayOfIndices ++ num.asString ++ ",";
						arrayOfMinusOnes = arrayOfMinusOnes ++ "-1.0,";
						arrayOfOnes = arrayOfOnes ++ "1.0,";
					});

					arrayOfIndices = arrayOfIndices[0..(arrayOfIndices.size - 2)] ++ "]";
					arrayOfMinusOnes = arrayOfMinusOnes[0..(arrayOfMinusOnes.size - 2)] ++ "]";
					arrayOfOnes = arrayOfOnes[0..(arrayOfOnes.size - 2)] ++ "]";
				});

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				//Not done already
				if(isAlreadyDone.isNil, {
					var indices_ar = "in;";
					var indices_kr = "in;";
					var scaling_ar = "Select.ar(\\useScaling.ir(0), [out, outScale]);";
					var scaling_kr = "Select.kr(\\useScaling.ir(0), [out, outScale]);";
					var env_ar = "\\env.ar(0);";
					var env_kr = "\\env.kr(0);";

					if(i > 1, {
						indices_ar = "Select.ar(\\indices.ir(" ++ arrayOfIndices ++ "), in);";
						indices_kr = "Select.kr(\\indices.ir(" ++ arrayOfIndices ++ "), in);";
					});

					[\ar_ar, \kr_kr, \ar_kr, \kr_ar].do({ | rate |
						var result;
						var name, in, indices, env, scaling;

						if(rate == \ar_ar, {
							name = "\\alga_interp_audio" ++ i ++ "_audio" ++ y;
							in = "\\in.ar(" ++ arrayOfZeros_in ++ ");";
							indices = indices_ar;
							scaling = scaling_ar;
							env = env_ar;
						});

						if(rate == \kr_kr, {
							name = "\\alga_interp_control" ++ i ++ "_control" ++ y;
							in = "\\in.kr(" ++ arrayOfZeros_in ++ ");";
							indices = indices_kr;
							scaling = scaling_kr;
							env = env_kr;
						});

						if(rate == \ar_kr, {
							name = "\\alga_interp_audio" ++ i ++ "_control" ++ y;
							in = "A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));";
							indices = indices_kr;
							scaling = scaling_kr;
							env = env_kr;
						});

						if(rate == \kr_ar, {
							name = "\\alga_interp_control" ++ i ++ "_audio" ++ y;
							in = "K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));";
							indices = indices_ar;
							scaling = scaling_ar;
							env = env_ar;
						});

						result = "
AlgaSynthDef(" ++ name ++ ", { | scaleCurve = 0, envCurve = \\sin |
var in, env, out, outScale;
in = " ++ in ++ "
out = " ++ indices ++ "
outScale = out.lincurve(
\\lowMin.ir(" ++ arrayOfMinusOnes ++ "),
\\lowMax.ir(" ++ arrayOfOnes ++ "),
\\highMin.ir(" ++ arrayOfMinusOnes ++ "),
\\highMax.ir(" ++ arrayOfOnes ++ "),
scaleCurve,
);
out = " ++ scaling ++ "
env = " ++ env ++ "
out = out * env;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

						result.interpret;

						//file.write(result ++ "\n");
					});

					alreadyDonePairs.put(currentPair, true);
				});

			});

		});

		//file.close; Document.open("~/AlgaSynthDefsTest.scd".standardizePath);
	}

	*initAlgaNorm {
		algaMaxIO.do({ | i |

			var result_audio, result_control;
			var arrayOfZeros = "[";

			i = i + 1;

			if(i == 1, {

				result_audio = "AlgaSynthDef(\\alga_norm_audio1, {
var in = \\in.ar(0);
var env = \\env.ar(0);
var out = Sanitize.ar(in / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef(\\alga_norm_control1, {
var in = \\in.kr(0).poll;
var env = \\env.kr(0);
var out = Sanitize.kr(in / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			}, {

				//Generate [0, 0, 0, ...
				(i + 1).do({ //+ 1 because of the env at last position
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_audio = "AlgaSynthDef(\\alga_norm_audio" ++ i.asString ++ ", {
var in = \\in.ar(" ++ arrayOfZeros ++ ");
var env = \\env.ar(0);
var out = Sanitize.ar(in / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

				result_control = "AlgaSynthDef(\\alga_norm_control" ++ i.asString ++ ", {
var in = \\args.kr(" ++ arrayOfZeros ++ ");
var env = \\env.kr(0);
var out = Sanitize.kr(in / env);
out;
}).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			});

			//Evaluate the generated code
			result_audio.interpret;
			result_control.interpret;

			//result_audio.postln;
			//result_control.postln;

		});
	}

	*initAlgaMixFades {
		algaMaxIO.do({ | i |
			var fadein_kr, fadein_ar;
			var fadeout_kr, fadeout_ar;

			i = i + 1;

			fadein_kr = "AlgaSynthDef(\\alga_fadeIn_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadein_ar = "AlgaSynthDef(\\alga_fadeIn_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([1, 0], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadeout_kr = "AlgaSynthDef(\\alga_fadeOut_control" ++ i.asString ++ ", { | curve = \\lin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = 0;
});
val[" ++ i ++ "] = EnvGen.kr(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadeout_ar = "AlgaSynthDef(\\alga_fadeOut_audio" ++ i.asString ++ ", { | curve = \\sin |
var val = Array.newClear(" ++ (i + 1) ++ ");
" ++ i ++ ".do({ | i |
val[i] = DC.ar(0);
});
val[" ++ i ++ "] = EnvGen.ar(Env([0, 1], #[1], curve), \\gate.kr(1), 1.0, 0.0, \\fadeTime.kr(0), Done.freeSelf);
val;
}, makeFadeEnv:false).writeDefFile(AlgaStartup.algaSynthDefIOPath);";

			fadein_kr.interpret;
			fadein_ar.interpret;
			fadeout_kr.interpret;
			fadeout_ar.interpret;
		});
	}
}
