AlgaNode {
	var <server;

	//Index of the corresponding AlgaBlock in the AlgaBlocksDict
	var <>blockIndex = -1;

	//This is the time when making a new connection to this node
	var <connectionTime = 0;

    //This controls the fade in and out when .play / .stop
    var <playTime = 0;

	//This is the longestConnectionTime between all the outNodes.
	//it's used when .replacing a node connected to something, in order for it to be kept alive
	//for all the connected nodes to run their interpolator on it
	//longestConnectionTime will be moved to AlgaBlock and applied per-block!
	var <connectionTimeOutNodes;
    var <longestConnectionTime = 0;

    //The max between longestConnectionTime and playTime
    var <longestWaitTime = 0;

	//This will be added: args passed in at creation to overwrite SynthDef's one,
	//When using <|, then, these are the ones that will be restored!
	var <objArgs;

	var <objClass;
	var <synthDef;

	var <controlNames;

	//per-parameter connectionTime
	var <paramsConnectionTime;

	var <numChannels, <rate;

	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup;
	var <playSynth, <synth, <normSynths, <interpSynths;
	var <synthBus, <normBusses, <interpBusses;

	var <inNodes, <outNodes;

	var <isPlaying = false;
	var <toBeCleared = false;
    var <beingStopped = false;

	*new { | obj, args, connectionTime = 0, playTime = 0, server |
		^super.new.init(obj, args, connectionTime, playTime, server)
	}

    init { | argObj, argArgs, argConnectionTime = 0, argPlayTime = 0, argServer |
		//Default server if not specified otherwise
		if(argServer == nil, { server = Server.default }, { server = argServer });

		//param -> ControlName
		controlNames = Dictionary(10);

		//param -> connectionTime
		paramsConnectionTime = Dictionary(10);

		//Per-argument dictionaries of interp/norm Busses and Synths belonging to this AlgaNode
		normBusses   = Dictionary(10);
		interpBusses = Dictionary(10);
		normSynths   = Dictionary(10);
		interpSynths = Dictionary(10);

		//Per-argument connections to this AlgaNode. These are in the form:
		//(param -> Set[AlgaNode, AlgaNode...]). Multiple AlgaNodes are used when
		//using the mixing <<+ / >>+
		inNodes = Dictionary.new(10);

		//outNodes are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a Set of all parameters
		//that it controls in that node (AlgaNode -> Set[\freq, \amp ...])
		outNodes = Dictionary.new(10);

		//Keeps all the connectionTimes of the connected nodes
		connectionTimeOutNodes = Dictionary.new(10);

		//starting connectionTime (using the setter so it also sets longestConnectionTime)
		this.connectionTime_(argConnectionTime, true);
        this.playTime_(argPlayTime);

		//Dispatch node creation
		this.dispatchNode(argObj, argArgs, true);
	}

	setParamsConnectionTime { | val, all = false, param |
		//If all, set all paramConnectionTime regardless of their previous value
		if(all, {
			paramsConnectionTime.keysValuesChange({ val });
		}, {
			//If not all, only set new param value if param != nil
			if(param != nil, {
				var paramConnectionTime = paramsConnectionTime[param];
				if(paramConnectionTime != nil, {
					paramsConnectionTime[param] = val;
				}, {
					("Invalid param to set connection time for: " ++ param).error;
				});
			}, {
				//This will just change the
				//paramConnectionTime for paramConnectionTimes that haven't been explicitly modified
				paramsConnectionTime.keysValuesChange({ | param, paramConnectionTime |
					if(paramConnectionTime == connectionTime, { val }, { paramConnectionTime });
				});
			});
		});
	}

	//connectionTime / connectTime / ct / interpolationTime / interpTime / it
	connectionTime_ { | val, all = false, param |
		if(val < 0, { val = 0 });
		//this must happen before setting connectionTime, as it's been used to set
		//paramConnectionTimes, checking against the previous connectionTime (before updating it)
		this.setParamsConnectionTime(val, all, param);

		//Only set global connectionTime if param is nil
		if(param == nil, {
			connectionTime = val;
		});

		this.calculateLongestConnectionTime(val);
	}

	//Convenience wrappers
	setAllConnectionTime { | val |
		this.connectionTime_(val, true);
	}

	allct { | val |
		this.connectionTime_(val, true);
	}

	allit { | val |
		this.connectionTime_(val, true);
	}

	act { | val |
		this.connectionTime_(val, true);
	}

	ait { | val |
		this.connectionTime_(val, true);
	}

	setParamConnectionTime { | param, val |
		this.connectionTime_(val, false, param);
	}

	paramct { | param, val |
		this.connectionTime_(val, false, param);
	}

	pct { | param, val |
		this.connectionTime_(val, false, param);
	}

	paramit { | param, val |
		this.connectionTime_(val, false, param);
	}

	pit { | param, val |
		this.connectionTime_(val, false, param);
	}

    connectTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

    connectTime { ^connectionTime }

	ct_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	ct { ^connectionTime }

    interpolationTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

    interpolationTime { ^connectionTime }

	interpTime_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	interpTime { ^connectionTime }

	it_ { | val, all = false, param | this.connectionTime_(val, all, param) }

	it { ^connectionTime }

	//playTime
    playTime_ { | val |
		if(val < 0, { val = 0 });
        playTime = val;
        this.calculateLongestWaitTime;
    }

    pt { ^playTime }

    pt_ { | val | this.playTime_(val) }

    //maximum between longestConnectionTime and playTime...
	//is this necessary? Yes it is, cause if running .clear on the receiver,
	//I need to be sure that if .replace or .clear is set to a sender, they will be longer
	//than the .playTime of the receiver being cleared.
    calculateLongestWaitTime {
        longestWaitTime = max(longestConnectionTime, playTime);
    }

	//calculate longestConnectionTime
	calculateLongestConnectionTime { | argConnectionTime, topNode = true |
		longestConnectionTime = max(connectionTime, argConnectionTime);

		//Doing the check instead of .max cause it's faster, imagine there are a lot of entries.
		connectionTimeOutNodes.do({ | val |
			if(val > longestConnectionTime, { longestConnectionTime = val });
		});

        this.calculateLongestWaitTime;

		//Only run this on the nodes that are strictly connected to the one
		//which calculateLongestConnectionTime was called on (topNode = true)
		if(topNode, {
			inNodes.do({ | sendersSet |
				sendersSet.do({ | sender |
					//Update sender's connectionTimeOutNodes and run same function on it
					sender.connectionTimeOutNodes[this] = longestConnectionTime;
                    sender.calculateLongestConnectionTime(longestConnectionTime, false);
				});
			});
		});
	}

	createAllGroups {
		if(group == nil, {
			group = Group(this.server);
            playGroup = Group(group);
			synthGroup = Group(group); //It could be ParGroup here for supernova
			normGroup = Group(group);
			interpGroup = Group(group);
		});
	}

	resetGroups {
		if(toBeCleared, {
            playGroup = nil;
			group = nil;
			synthGroup = nil;
			normGroup = nil;
			interpGroup = nil;
		});
	}

	//Groups (and state) will be reset only if they are nil AND they are set to be freed.
	//the toBeCleared variable can be changed in real time, if AlgaNode.replace is called while
	//clearing is happening!
	freeAllGroups { | now = false |
		if((group != nil).and(toBeCleared), {
			if(now, {
				//Free now
				group.free;

				//this.resetGroups;
			}, {
				//Wait longestWaitTime, then free
				fork {
					longestWaitTime.wait;

					group.free;

					//this.resetGroups;
				};
			});
		});
	}

	createSynthBus {
		synthBus = AlgaBus(server, numChannels, rate);
	}

	createInterpNormBusses {
		controlNames.do({ | controlName |
			var paramName = controlName.name;

			var argDefaultVal = controlName.defaultValue;
			var paramRate = controlName.rate;
			var paramNumChannels = controlName.numChannels;

			//interpBusses have 1 more channel for the envelope shape
			interpBusses[paramName] = AlgaBus(server, paramNumChannels + 1, paramRate);
			normBusses[paramName] = AlgaBus(server, paramNumChannels, paramRate);
		});
	}

	createAllBusses {
		this.createInterpNormBusses;
		this.createSynthBus;
	}

	freeSynthBus { | now = false |
		if(now, {
			if(synthBus != nil, { synthBus.free });
		}, {
			//if forking, this.synthBus could have changed, that's why this is needed
			var prevSynthBus = synthBus.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				if(prevSynthBus != nil, { prevSynthBus.free });
			}
		});
	}

	freeInterpNormBusses { | now = false |
		if(now, {
			//Free busses now
			if(normBusses != nil, {
				normBusses.do({ | normBus |
					if(normBus != nil, { normBus.free });
				});
			});

			if(normBusses != nil, {
				normBusses.do({ | interpBus |
					if(interpBus != nil, { interpBus.free });
				});
			});
		}, {
			//Dictionary need to be deepcopied
			var prevNormBusses = normBusses.copy;
			var prevInterpBusses = interpBusses.copy;

			//Free prev busses after longestWaitTime
			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestConnectionTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				if(prevNormBusses != nil, {
					prevNormBusses.do({ | normBus |
						if(normBus != nil, { normBus.free });
					});
				});

				if(prevInterpBusses != nil, {
					prevInterpBusses.do({ | interpBus |
						if(interpBus != nil, { interpBus.free });
					});
				});
			}
		});
	}

	freeAllBusses { | now = false |
		this.freeSynthBus(now);
		this.freeInterpNormBusses(now);
	}

	//dispatches controlnames / numChannels / rate according to obj class
	dispatchNode { | obj, args, initGroups = false, replace = false |
		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, args, initGroups, replace);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction(obj, args, initGroups, replace);
			}, {
				("AlgaNode: class '" ++ objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	//Dispatch a SynthDef
	dispatchSynthDef { | obj, args, initGroups = false, replace = false |
		var synthDescControlNames;
		var synthDesc = SynthDescLib.global.at(obj);

		if(synthDesc == nil, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++ "'").error;
			this.clear;
			^nil;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
			this.clear;
			^nil;
		});

		synthDescControlNames = synthDesc.controls;
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Create all utilities
		if(initGroups, { this.createAllGroups });
		this.createAllBusses;

		//Create actual synths
		this.createAllSynths(synthDef.name, replace);
	}

	//Dispatch a Function
	dispatchFunction { | obj, args, initGroups = false, replace = false |
		//Need to wait for server's receiving the sdef
		fork {
			var synthDescControlNames;

			synthDef = AlgaSynthDef(("alga_" ++ UniqueID.next).asSymbol, obj).send(server);
			server.sync;

			synthDescControlNames = synthDef.asSynthDesc.controls;
			this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

			numChannels = synthDef.numChannels;
			rate = synthDef.rate;

			//Create all utilities
			if(initGroups, { this.createAllGroups });
			this.createAllBusses;

			//Create actual synths
			this.createAllSynths(synthDef.name, replace);
		};
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNamesAndParamsConnectionTime { | synthDescControlNames |
		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if((controlName.name != \fadeTime).and(
				controlName.name != \out).and(
				controlName.name != \gate).and(
				controlName.name != '?'), {

				var paramName = controlName.name;

				//Create controlNames
				controlNames[paramName] = controlName;

				//Create paramsConnectionTime
				paramsConnectionTime[paramName] = connectionTime;
			});
		});
	}

	resetSynth {
		if(toBeCleared, {
			//Set to nil (should it fork?)
			synth = nil;
			synthDef = nil;
			controlNames.clear;
			numChannels = 0;
			rate = nil;
		});
	}

	resetInterpNormSynths {
		if(toBeCleared, {
			//Just reset the Dictionaries entries
			interpSynths.clear;
			normSynths.clear;
		});
	}

	//Synth writes to the synthBus
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs when running .replace!
	createSynth { | defName |
		//synth's \fadeTime is longestWaitTime...It could probably be removed here
		var synthArgs = [\out, synthBus.index, \fadeTime, longestWaitTime];

        /*
		//Add the param busses (which have already been allocated)
		//Should this connect here or in createInterpNormSynths
		normBusses.keysValuesDo({ | param, normBus |
		synthArgs = synthArgs.add(param);
		synthArgs = synthArgs.add(normBus.busArg);
		});
		*/

		synth = AlgaSynth.new(
			defName,
			synthArgs,
			synthGroup
		);
	}

	//This should take in account the nextNode's numChannels when making connections
	createInterpNormSynths { | replace = false |
		controlNames.do({ | controlName |
			var interpSymbol, normSymbol;
			var interpBus, normBus, interpSynth, normSynth;

			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels.asString;
			var paramRate = controlName.rate.asString;
			var paramDefault = controlName.defaultValue;

			//e.g. \algaInterp_audio1_control1
			interpSymbol = (
				"algaInterp_" ++
				paramRate.asString ++
				paramNumChannels.asString ++
				"_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			//e.g. \algaNorm_audio1
			normSymbol = (
				"algaNorm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			interpBus = interpBusses[paramName];
			normBus = normBusses[paramName];

            //If replace, connect to the pervious bus, not default
            //This wouldn't work with mixing for now...
            if(replace, {
                var sendersSet = inNodes[paramName];
                if(sendersSet.size > 1, { "Restoring mixing parameters is not implemented yet"; ^nil; });
                if(sendersSet != nil, {
                    if(sendersSet.size == 1, {
                        var prevSender;
                        sendersSet.do({ | sender | prevSender = sender }); //Sets can't be indexed, need to loop over

                        //It would be cool if I could keep the same interpSynth as before if it has same number
                        //of channels as the new one, so that it could continue the interpolation of the previous node,
                        //if one was taking place...
                        interpSynth = AlgaSynth.new(
                            interpSymbol,
                            [\in, prevSender.synthBus.busArg, \out, interpBus.index, \fadeTime, 0],
                            interpGroup
                        )
                    })
                }, {
                    //sendersSet is nil, run the normal one
                    interpSynth = AlgaSynth.new(
                        interpSymbol,
                        [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                        interpGroup
                    );
                })
            }, {
                //No previous nodes connected: create a new interpSynth with the paramDefault value
                //Instantiated right away, with no \fadeTime, as it will directly be connected to
                //synth's parameter
                interpSynth = AlgaSynth.new(
                    interpSymbol,
                    [\in, paramDefault, \out, interpBus.index, \fadeTime, 0],
                    interpGroup
                );
            });

			//Instantiated right away, with no \fadeTime, as it will directly be connected to
			//synth's parameter (synth is already reading from all the normBusses)
			normSynth = AlgaSynth.new(
				normSymbol,
				[\args, interpBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			interpSynths[paramName] = interpSynth;
			normSynths[paramName] = normSynth;

			//Connect synth's parameter to the normBus
			synth.set(paramName, normBus.busArg);
		});
	}

	createAllSynths { | defName, replace = false |
		this.createSynth(defName);
		this.createInterpNormSynths(replace);
	}

	//Used at every << / >> / <|
	createInterpSynthAtParam { | sender, param = \in |
		var controlName, paramConnectionTime;
		var paramNumChannels, paramRate;
		var senderNumChannels, senderRate;
		var interpSymbol;

		var interpBus, interpSynth;

		controlName = controlNames[param];
		paramConnectionTime = paramsConnectionTime[param];

		if((controlName.isNil).or(paramConnectionTime.isNil), {
			("Invalid param for interp synth to free: " ++ param).error;
			^this
		});

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

		paramNumChannels = controlName.numChannels;
		paramRate = controlName.rate;

		if(sender.isAlgaNode, {
			// Used in << / >>
			senderNumChannels = sender.numChannels;
			senderRate = sender.rate;
		}, {
			//Used in <| AND << with number/array
			senderNumChannels = paramNumChannels;
			senderRate = paramRate;
		});

		interpSymbol = (
			"algaInterp_" ++
			senderRate ++
			senderNumChannels ++
			"_" ++
			paramRate ++
			paramNumChannels
		).asSymbol;

		interpBus = interpBusses[param];

		//new interp synth, with input connected to sender and output to the interpBus
		//THIS USES connectionTime!!
        //THIS, together with freeInterpSynthAtParam, IS THE WHOLE CORE OF THE INTERPOLATION BEHAVIOUR!!!
		if(sender.isAlgaNode, {
			//Used in << / >>
			//Read \in from the sender's synthBus
            interpSynth = AlgaSynth.new(
                interpSymbol,
                [\in, sender.synthBus.busArg, \out, interpBus.index, \fadeTime, paramConnectionTime],
                interpGroup
            );
		}, {
			//Used in <| AND << with number / array
			//if sender is nil, restore the original default value. This is used in <|
			var paramVal;
			if(sender == nil, {
				paramVal = controlName.defaultValue;
			}, {
                //If not nil, check if it's a number or array. Use it if that's the case
				if(sender.isNumberOrArray,  {
					paramVal = sender
				}, {
					"Invalid paramVal for AlgaNode".error;
					^nil;
				});
			});

			interpSynth = AlgaSynth.new(
				interpSymbol,
				[\in, paramVal, \out, interpBus.index, \fadeTime, paramConnectionTime],
				interpGroup
			);
		});

		//Add synth to interpSynths
		interpSynths[param] = interpSynth;
	}


	//Default now and useConnectionTime to true for synths.
	//Synth always uses longestConnectionTime, in order to make sure that everything connected to it
	//will have time to run fade ins and outs
	freeSynth { | useConnectionTime = true, now = true |
		if(now, {
			if(synth != nil, {
				//synth's fadeTime is longestWaitTime!
				synth.set(\gate, 0, \fadeTime, if(useConnectionTime, { longestWaitTime }, { 0 }));

				//this.resetSynth;
			});
		}, {
            //Needs to be deep copied (a new synth could be instantiated meanwhile)
			var prevSynth = synth.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				if(prevSynth != nil, {
					prevSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	//Default now and useConnectionTime to true for synths
	freeInterpNormSynths { | useConnectionTime = true, now = true |
		if(now, {
			//Free synths now
			interpSynths.do({ | interpSynth |
				interpSynth.set(\gate, 0, \fadeTime, if(useConnectionTime, { longestWaitTime }, {0}));
			});

			normSynths.do({ | normSynth |
				normSynth.set(\gate, 0, \fadeTime, if(useConnectionTime, { longestWaitTime }, {0}));
			});

			//this.resetInterpNormSynths;
		}, {
			//Dictionaries need to be deep copied
			var prevInterpSynths = interpSynths.copy;
			var prevNormSynths = normSynths.copy;

			fork {
				//Cheap solution when having to replacing a synth that had other interp stuff
				//going on. Simply wait longer than longestWaitTime (which will be the time the replaced
				//node will take to interpolate to the previous receivers) and then free all the previous stuff
				(longestWaitTime + 1.0).wait;

				prevInterpSynths.do({ | interpSynth |
					interpSynth.set(\gate, 0, \fadeTime, 0);
				});

				prevNormSynths.do({ | normSynth |
					normSynth.set(\gate, 0, \fadeTime, 0);
				});
			}
		});
	}

	freeAllSynths { | useConnectionTime = true, now = true |
		this.freeInterpNormSynths(useConnectionTime, now);
		this.freeSynth(useConnectionTime, now);
	}

	freeAllSynthOnNewInstantiation { | useConnectionTime = true, now = true |
		this.freeAllSynths(useConnectionTime, now);
	}

	//This is only used in connection situations
	//THIS USES connectionTime!!
    //THIS, TOGETHER WITH createInterpSynthAtParam, IS THE WHOLE CORE OF THE INTERPOLATION BEHAVIOUR!!!
	freeInterpSynthAtParam { | param = \in |
		var interpSynthAtParam = interpSynths[param];
		var paramConnectionTime = paramsConnectionTime[param];

		if((interpSynthAtParam.isNil).or(paramConnectionTime.isNil), {
			("Invalid param for interp synth to free: " ++ param).error;
			^this
		});

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

        interpSynthAtParam.set(\gate, 0, \fadeTime, paramConnectionTime);
	}

	//param -> Set[AlgaNode, AlgaNode, ...]
	addInNode { | sender, param = \in, mix = false |
		//First of all, remove the outNodes that the previous sender had with the
		//param of this node, if there was any. Only apply if mix==false (no <<+ / >>+)
		if(mix == false, {
			var previousSenderSet = inNodes[param];
			if(previousSenderSet != nil, {
				previousSenderSet.do({ | previousSender |
					previousSender.outNodes.removeAt(this);
				});
			});
		});

		if(sender.isAlgaNode, {
			//Empty entry OR not doing mixing, create new Set. Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = Set[sender];
			}, {
				inNodes[param].add(sender);
			})
		});
	}

	//AlgaNode -> Set[param, param, ...]
	addOutNode { | receiver, param = \in |
		//Empty entry, create Set. Otherwise, add to existing
		if(outNodes[receiver] == nil, {
			outNodes[receiver] = Set[param];
		}, {
			outNodes[receiver].add(param);
		});
	}

	//add entries to the inNodes / outNodes / connectionTimeOutNodes of the two AlgaNodes
	addInOutNodesDict { | sender, param = \in |
		//This will replace the entries on new connection (as mix == false)
		this.addInNode(sender, param);

		//This will add the entries to the existing Set, or create a new one
		if(sender.isAlgaNode, {
			sender.addOutNode(this, param);

			//Add to connectionTimeOutNodes and recalculate longestConnectionTime
			sender.connectionTimeOutNodes[this] = this.connectionTime;
			sender.calculateLongestConnectionTime(this.connectionTime);
		});
	}

	removeInOutNode { | sender, param = \in |
		sender.outNodes[this].remove(param);
		inNodes[param].remove(sender);

		//Recalculate longestConnectionTime too...
		//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
		//(Right now, this happens before creating new synths)
		sender.connectionTimeOutNodes[this] = 0;
		sender.calculateLongestConnectionTime(0);
	}

	//Remove entries from inNodes / outNodes / connectionTimeOutNodes for all involved nodes
	removeInOutNodesDict { | previousSender = nil, param = \in |
		var previousSenders = inNodes[param];
		if(previousSenders == nil, { /*( "No previous connection enstablished at param:" ++ param).error;*/ ^this; });

		previousSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				//Multiple entries in the set
				if(sendersParamsSet.size > 1, {
					//no previousSender specified: remove them all!
					if(previousSender == nil, {
						this.removeInOutNode(sender, param);
					}, {
						//If specified previousSender, only remove that one (in mixing scenarios)
						if(sender == previousSender, {
							this.removeInOutNode(sender, param);
						})
					})
				}, {
					//If Set with just one entry, remove the entire Set
					sender.outNodes.removeAt(this);

					//Recalculate longestConnectionTime too...
					//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
					//(Right now, this happens before creating new synths)
					sender.connectionTimeOutNodes[this] = 0;
					sender.calculateLongestConnectionTime(0);
				})
			})
		});

		//If Set with just one entry, remove the entire Set
		if(previousSenders.size == 1, {
			inNodes.removeAt(param);
		})
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(toBeCleared, {
			inNodes.clear;
			outNodes.clear;
		});
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to create a new interp synth for: " ++ param).error; ^this; });

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if(replace.not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//Free previous interp synth (fades out)
        this.freeInterpSynthAtParam(param);

        //Spawn new interp synth (fades in)
        this.createInterpSynthAtParam(sender, param);
	}

	//Used in <|
	removeInterpConnectionAtParam { | previousSender = nil, param = \in  |
		var controlName = controlNames[param];
		if(controlName == nil, { ("Invalid param to reset: " ++ param).error; ^this; });

		//Remove inNodes / outNodes / connectionTimeOutNodes
		this.removeInOutNodesDict(previousSender, param);

		//Re-order groups shouldn't be needed when removing connections

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(param);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param);
	}

	//implements receiver <<.param sender
	makeConnection { | sender, param = \in, replace = false |
		//Can't connect AlgaNode to itself
		if(this === sender, { "Can't connect an AlgaNode to itself".error; ^this });

		//Connect interpSynth to the sender's synthBus
		AlgaSpinRoutine.waitFor( { (this.instantiated).and(sender.instantiated) }, {
			this.newInterpConnectionAtParam(sender, param, replace:replace);
		});
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
            receiver.makeConnection(this, param);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param);
		}, {
			("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
		});
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
        if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
            receiver.makeConnection(this, param);
        }, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
        });
	}

    //Replace a mix entry ???
    <<! { | sender, previousSender, param = \in |

    }

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//previousSender is used in case of mixing, to only remove that one
	<| { | param = \in, previousSender = nil |
		//Also remove inNodes / outNodes / connectionTimeOutNodes
		if(previousSender != nil, {
			if(previousSender.isAlgaNode, {
				AlgaSpinRoutine.waitFor( { (this.instantiated).and(previousSender.instantiated) }, {
					this.removeInterpConnectionAtParam(previousSender, param);
				});
			}, {
				("Trying to remove a connection to an invalid AlgaNode: " ++ previousSender).error;
			})
		}, {
			AlgaSpinRoutine.waitFor( { this.instantiated }, {
				this.removeInterpConnectionAtParam(nil, param);
			});
		})
	}

	//replace connections FROM this
	replaceConnections {
        //inNodes are already handled in dispatchNode(replace:true)

		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				receiver.makeConnection(this, param, replace:true);
			});
		});
	}

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | obj, args |
        var wasPlaying = false;
		//re-init groups if clear was used
		var initGroups = if(group == nil, { true }, { false });

		//In case it has been set to true when clearing, then replacing before clear ends!
		toBeCleared = false;

        //If it was playing, free previous playSynth
        if(isPlaying, {
            this.stop;
            wasPlaying = true;
        });

		//This doesn't work with feedbacks, as synths would be freed slightly before
		//The new ones finish the rise, generating click. These should be freed
		//When the new synths/busses are surely instantiated on the server!
		//The cheap solution that it's in place now is to wait 0.5 longer than longestConnectionTime...
		//Work out a better solution!
		this.freeAllSynths(false, false);
		this.freeAllBusses;

		//New one
		this.dispatchNode(obj, args, initGroups, true);

		//Re-enstablish connections that were already in place
		this.replaceConnections;

        //If node was playing, or .replace has been called while .stop / .clear, play again
        if(wasPlaying.or(beingStopped), {
            this.play;
        })
	}

	//Execute <| on all outNodes' parameters that are connected to this
	removeConnectionFromReceivers {
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				receiver.perform('<|', param);
			});
		});
	}

	//Clears it all... It should do some sort of fading
	clear {
		//If synth had connections, run <| on the receivers (so it resets to defaults)
		this.removeConnectionFromReceivers;

		//This could be overwritten if .replace is called
		toBeCleared = true;

        //Stop playing (if it was playing at all)
        this.freePlaySynth();

		fork {
			//Wait time before clearing groups and busses...
			longestWaitTime.wait;

			//this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true); //I can just remove the groups, as they contain the synths
			this.freeAllBusses(true);

			//Reset all instance variables
			this.resetSynth;
			this.resetInterpNormSynths;
			this.resetGroups;
			this.resetInOutNodesDicts;
		}
	}

	//All synths must be instantiated (including interpolators and normalizers)
	instantiated {
		if(synth == nil, { ^false });

		interpSynths.do({ | interpSynth |
			if(interpSynth.instantiated == false, { ^false });
		});

		normSynths.do({ | normSynth |
			if(normSynth.instantiated == false, { ^false });
		});

		//Lastly, the actual synth
		^synth.instantiated;
	}

	//Move this node's group before another node's one
	moveBefore { | node |
		group.moveBefore(node.group);
	}

	//Move this node's group after another node's one
	moveAfter { | node |
		group.moveAfter(node.group);
	}

    createPlaySynth {
        if((isPlaying.not).or(beingStopped), {
            var playSynthSymbol;

            if(rate == \control, { "Cannot play a kr AlgaNode".error; ^nil; });

            playSynthSymbol = ("alga_play_" ++ numChannels).asSymbol;

            playSynth = Synth(
                playSynthSymbol,
                [\in, synthBus.busArg, \gate, 1, \fadeTime, playTime],
                playGroup
            );
            isPlaying = true;
            beingStopped = false;
        })
    }

    freePlaySynth {
        if(isPlaying, {
            playSynth.set(\gate, 0, \fadeTime, playTime);
            isPlaying = false;
            beingStopped = true;
        })
    }

	play {
		AlgaSpinRoutine.waitFor({ this.instantiated }, {
            this.createPlaySynth;
		});
	}

    stop {
		AlgaSpinRoutine.waitFor({ this.instantiated }, {
            this.freePlaySynth;
		});
    }

	isAlgaNode { ^true }
}

//Alias
AN : AlgaNode {}