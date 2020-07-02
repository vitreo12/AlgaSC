AlgaStartup {

	*initSynthDefs {
		arg server = Server.local;

		var alreadyDonePairs = Dictionary.new;

		//Using add is much faster than store/read...

        16.do({ | i |
            var sdef, arrayOfZeros_in;
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

            sdef = "
            AlgaSynthDef(\\alga_play_" ++ i ++ ", {
                Out.ar(\\out.ir(0), \\in.ar(" ++ arrayOfZeros_in ++ ") * AlgaEnvGate.ar)
            }).add;
            ";

            sdef.interpret;
        });

		16.do({
			arg i;

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

			16.do({
				arg y;

				var currentPair, isAlreadyDone;

				y = y + 1;

				currentPair = [i, y];
				isAlreadyDone = alreadyDonePairs[currentPair];

				//Not done already
				if(isAlreadyDone != true, {

					var result_audio_audio, result_control_control, result_audio_control, result_control_audio;

					if(i >= y, {

						if(i == 1, {
							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						}, {
							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out[i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						});

					});

					if(i < y, {

						if(i == 1, {

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(0);
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(0);
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(0));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(0));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
outs[i] = out;
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						}, {

							//ar -> ar
							result_audio_audio = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_audio" ++ y ++ ", {
var in = \\in.ar(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> kr
							result_control_control = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_control" ++ y ++ ", {
var in = \\in.kr(" ++ arrayOfZeros_in ++ ");
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//ar -> kr
							result_audio_control = "
AlgaSynthDef(\\algaInterp_audio" ++ i ++ "_control" ++ y ++ ", {
var in = A2K.kr(\\in.ar(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.kr(i_level: 0, doneAction:2, curve: \\lin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

							//kr -> ar
							result_control_audio = "
AlgaSynthDef(\\algaInterp_control" ++ i ++ "_audio" ++ y ++ ", {
var in = K2A.ar(\\in.kr(" ++ arrayOfZeros_in ++ "));
var env = AlgaEnvGate.ar(i_level: 0, doneAction:2, curve: \\sin);
var out = in * env;
var outs = Array.newClear(" ++ (y + 1) ++ ");
" ++ y ++ ".do({
arg i;
var mod_i = i % " ++ i ++ ";
outs[i] = out[mod_i];
});
outs[" ++ y ++ "] = env;
outs;
}, makeFadeEnv:false).add;";

						});

					});

					alreadyDonePairs.put(currentPair, true);

					result_audio_audio.interpret;
					result_control_control.interpret;
					result_audio_control.interpret;
					result_control_audio.interpret;

					//result_audio_audio.postln;
					//result_control_control.postln;
					//result_audio_control.postln;
					//result_control_audio.postln;

				});

			});

		});

		//interpolationProxyNormalizer
		16.do({
			arg counter;

			var result_audio, result_control;
			var arrayOfZeros = "[";

			counter = counter + 1;

			if(counter == 1, {

				result_audio = "AlgaSynthDef(\\algaNorm_audio1, {
var args = \\args.ar([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.ar(val / env);
out;
}).add;";

				result_control = "AlgaSynthDef(\\algaNorm_control1, {
var args = \\args.kr([0, 0]);
var val = args[0];
var env = args[1];
var out = Sanitize.kr(val / env);
out;
}).add;";

			}, {

				//Generate [0, 0, 0, ...
				(counter + 1).do({
					arrayOfZeros = arrayOfZeros ++ "0,";
				});

				//remove trailing coma [0, 0, 0, and enclose in bracket -> [0, 0, 0]
				arrayOfZeros = arrayOfZeros[0..(arrayOfZeros.size - 2)] ++ "]";

				result_audio = "AlgaSynthDef(\\algaNorm_audio" ++ counter.asString ++ ", {
var args = \\args.ar( " ++ arrayOfZeros ++ ");
var val = args[0.." ++ (counter - 1).asString ++ "];
var env = args[" ++ counter.asString ++ "];
var out = Sanitize.ar(val / env);
out;
}).add;";

				result_control = "AlgaSynthDef(\\algaNorm_control" ++ counter.asString ++ ", {
var args = \\args.kr( " ++ arrayOfZeros ++ ");
var val = args[0.." ++ (counter - 1).asString ++ "];
var env = args[" ++ counter.asString ++ "];
var out = Sanitize.kr(val / env);
out;
}).add;";

			});

			//Evaluate the generated code
			result_audio.interpret;
			result_control.interpret;

			//result_audio.postln;
			//result_control.postln;

		});
	}

}
