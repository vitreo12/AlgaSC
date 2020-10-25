AlgaNode {
	//Server where this node lives
	var <server;

	//Index of the corresponding AlgaBlock in the AlgaBlocksDict.
	//This is being set in AlgaBlock
	var <>blockIndex = -1;

	//This is the time when making a new connection to this node
	var <connectionTime = 0;

	//This controls the fade in and out when .play / .stop
	var <playTime = 0;

	//The algaScheduler @ this server
	var <algaScheduler;

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

	var <outs;

	var <group, <playGroup, <synthGroup, <normGroup, <interpGroup;
	var <playSynth, <synth, <normSynths, <interpSynths, <envSynths;
	var <synthBus, <normBusses, <interpBusses, <envBusses;

	var <inNodes, <outNodes;

	//keep track of current \default nodes
	var <currentDefaultNodes;

	//keep track of current scaling for params
	var <paramsScalings;

	var <paramsChansMapping;

	var <isPlaying = false;
	var <toBeCleared = false;
	var <beingStopped = false;
	var <cleared = false;

	*new { | obj, args, connectionTime = 0, playTime = 0, outsMapping, server |
		^super.new.init(obj, args, connectionTime, playTime, outsMapping, server)
	}

	init { | argObj, argArgs, argConnectionTime = 0, argPlayTime = 0, argOutsMapping, argServer |
		//Default server if not specified otherwise
		server = argServer ? Server.default;

		//AlgaScheduler from specific server
		algaScheduler = Alga.getScheduler(server);
		if(algaScheduler == nil, {
			(
				"Can't retrieve correct AlgaScheduler for server " ++
				server.name ++
				". Has Alga.boot been called on it?"
			).error;
			^nil;
		});

		//param -> ControlName
		controlNames = IdentityDictionary(10);

		//param -> val
		objArgs = IdentityDictionary(10);

		//param -> connectionTime
		paramsConnectionTime = IdentityDictionary(10);

		//These are only one per param. All the mixing normalizers will be summed at the bus anyway.
		//\param -> normBus
		normBusses   = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> interpBus)
		interpBusses = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> envBus)
		envBusses = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> normSynth)
		normSynths   = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> interpSynth)
		interpSynths = IdentityDictionary(10);

		//IdentityDictionary of IdentityDictonaries: (needed for mixing)
		//\param -> IdentityDictionary(sender -> envSynth)
		envSynths = IdentityDictionary(10);

		//Per-argument connections to this AlgaNode. These are in the form:
		//(param -> IdentitySet[AlgaNode, AlgaNode...]). Multiple AlgaNodes are used when
		//using the mixing <<+ / >>+
		inNodes = IdentityDictionary(10);

		//outNodes are not indexed by param name, as they could be attached to multiple nodes with same param name.
		//they are indexed by identity of the connected node, and then it contains a IdentitySet of all parameters
		//that it controls in that node (AlgaNode -> IdentitySet[\freq, \amp ...])
		outNodes = IdentityDictionary(10);

		//Chans mapping from inNodes... How to support <<+ / >>+ ???
		//IdentityDictionary of IdentityDictionaries:
		//\param -> IdentityDictionary(sender -> paramChanMapping)
		paramsChansMapping = IdentityDictionary(10);

		//Keep track of the scale arguments for senders (for replace calls)
		//\param -> IdentityDictionary(sender -> scale)
		paramsScalings = IdentityDictionary(10);

		//This keeps track of current \default nodes for every param.
		//These are then used to restore default connections on <| or << after the param being a mix one (<<+)
		currentDefaultNodes = IdentityDictionary(10);

		//Keeps all the connectionTimes of the connected nodes
		connectionTimeOutNodes = IdentityDictionary(10);

		//starting connectionTime (using the setter so it also sets longestConnectionTime)
		this.connectionTime_(argConnectionTime, true);
		this.playTime_(argPlayTime);

		//Dispatch node creation
		this.dispatchNode(argObj, argArgs, initGroups:true, outsMapping:argOutsMapping);
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
	calculateLongestWaitTime { | time |
		longestWaitTime = max(longestConnectionTime, playTime);
	}

	//2 args: new time and original time.
	//return time, but also set longestWaitTime accordingly
	calculateTemporaryLongestWaitTime { | time, otherTime |
		//if nil time, return otherTime (the original one)
		if((time == nil).or(time.isNumber.not), { ^otherTime });

		//this is to set a temporary longestWaitTime
		if(time > longestWaitTime, {
			var prevLongestWaitTime = longestWaitTime;
			longestWaitTime = time;

			//after time, calculate values again
			fork {
				time.wait;

				//might have changed meanwhile (due to another function)
				if(longestWaitTime != time, {
					//use the longest available
					//(remember: longestWaitTime here has been changed from time)
					longestWaitTime = max(longestWaitTime, prevLongestWaitTime);
				}, {
					//Restore previous one: nothing changed
					longestWaitTime = prevLongestWaitTime;
				})
			}
		});

		//return time
		^time;
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

	outsMapping {
		^outs
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
			var paramRate = controlName.rate;
			var paramNumChannels = controlName.numChannels;

			interpBusses[paramName][\default] = AlgaBus(server, paramNumChannels, paramRate);
			envBusses[paramName][\default] = AlgaBus(server, 1, paramRate);
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

	//This will also be kept across replaces, as it's just updating the dict
	createObjArgs { | args |
		if(args != nil, {
			if(args.isSequenceableCollection.not, { "AlgaNode: args must be an array".error; ^this });
			if((args.size) % 2 != 0, { "AlgaNode: args' size must be a power of two".error; ^this });

			args.do({ | param, i |
				if(param.class == Symbol, {
					var iPlusOne = i + 1;
					if(iPlusOne < args.size, {
						var val = args[i+1];
						if((val.isNumberOrArray).or(val.isAlgaNode), {
							objArgs[param] = val;
						}, {
							("AlgaNode: args at param " ++ param ++ " must be a number, array or AlgaNode").error;
						});
					});
				});
			});
		});
	}

	//dispatches controlnames / numChannels / rate according to obj class
	dispatchNode { | obj, args, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false |

		objClass = obj.class;

		//If there is a synth playing, set its instantiated status to false:
		//this is mostly needed for .replace to work properly and wait for the new synth
		//to be instantiated!
		if(synth != nil, { synth.instantiated = false });

		//Create args dict
		this.createObjArgs(args);

		//Symbol
		if(objClass == Symbol, {
			this.dispatchSynthDef(obj, initGroups, replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale
			);
		}, {
			//Function
			if(objClass == Function, {
				this.dispatchFunction(obj, initGroups, replace,
					keepChannelsMapping:keepChannelsMapping,
					outsMapping:outsMapping,
					keepScale:keepScale
				);
			}, {
				("AlgaNode: class '" ++ objClass ++ "' is invalid").error;
				this.clear;
			});
		});
	}

	//Remove \fadeTime \out and \gate and generate controlNames dict entries
	createControlNamesAndParamsConnectionTime { | synthDescControlNames |
		//Reset entries first (but not paramsConnectionTime, reusing old params' one? )
		controlNames.clear;

		synthDescControlNames.do({ | controlName |
			var paramName = controlName.name;
			if((controlName.name != \fadeTime).and(
				controlName.name != \out).and(
				controlName.name != \gate).and(
				controlName.name != '?'), {

				var paramName = controlName.name;

				//Create controlNames
				controlNames[paramName] = controlName;

				//Create paramsConnectionTime ... keeping same value among .replace calls.
				//Only replace if entry is clear
				if(paramsConnectionTime[paramName] == nil, {
					paramsConnectionTime[paramName] = connectionTime;
				});

				//Create IdentityDictionaries for everything needed
				paramsChansMapping[paramName] = IdentityDictionary();
				interpSynths[paramName] = IdentityDictionary();
				envSynths[paramName] = IdentityDictionary();
				normSynths[paramName] = IdentityDictionary();
				interpBusses[paramName] = IdentityDictionary();
				envBusses[paramName] = IdentityDictionary();
			});
		});
	}

	//calculate the outs variable (the outs channel mapping)
	calculateOuts { | replace = false, keepChannelsMapping = false |
		//Accumulate channelsMapping across .replace calls.
		if(replace.and(keepChannelsMapping), {
			var newOuts = IdentityDictionary(10);

			//copy previous ones
			outs.keysValuesDo({ | key, value |
				//Delete out of bounds entries? Or keep them for future .replaces?
				//if(value < numChannels, {
				newOuts[key] = value;
				//});
			});

			//new ones from the synthDef
			synthDef.outsMapping.keysValuesDo({ | key, value |
				//Delete out of bounds entries? Or keep them for future .replaces?
				//if(value < numChannels, {
				newOuts[key] = value;
				//});
			});

			outs = newOuts;
		}, {
			//no replace: use synthDef's ones
			outs = synthDef.outsMapping;
		});
	}

	//build all synths
	buildFromSynthDef { | initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false |

		//Retrieve controlNames from SynthDesc
		var synthDescControlNames = synthDef.asSynthDesc.controls;
		this.createControlNamesAndParamsConnectionTime(synthDescControlNames);

		numChannels = synthDef.numChannels;
		rate = synthDef.rate;

		//Generate outs (for outsMapping connectinons)
		this.calculateOuts(replace, keepChannelsMapping);

		//Create groups if needed
		if(initGroups, { this.createAllGroups });

		//Create busses
		this.createAllBusses;

		//Create actual synths
		this.createAllSynths(
			replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale
		);
	}

	//Dispatch a SynthDef (symbol)
	dispatchSynthDef { | obj, initGroups = false, replace = false,
		keepChannelsMapping = false, keepScale = false |

		var synthDesc = SynthDescLib.global.at(obj);

		if(synthDesc == nil, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++ "'").error;
			this.clear;
			^this;
		});

		synthDef = synthDesc.def;

		if(synthDef.class != AlgaSynthDef, {
			("Invalid AlgaSynthDef: '" ++ obj.asString ++"'").error;
			this.clear;
			^this;
		});

		this.buildFromSynthDef(
			initGroups, replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale
		);
	}

	//Dispatch a Function
	dispatchFunction { | obj, initGroups = false, replace = false,
		keepChannelsMapping = false, outsMapping, keepScale = false |

		//Need to wait for server to receive the sdef
		fork {
			synthDef = AlgaSynthDef(
				("alga_" ++ UniqueID.next).asSymbol,
				obj,
				outsMapping:outsMapping
			).send(server);

			server.sync;

			this.buildFromSynthDef(
				initGroups, replace,
				keepChannelsMapping:keepChannelsMapping,
				keepScale:keepScale
			);
		};
	}

	resetSynth {
		if(toBeCleared, {
			//IdentitySet to nil (should it fork?)
			synth = nil;
			synthDef = nil;
			controlNames.clear;
			paramsConnectionTime.clear;
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
	createSynth {
		var defName = synthDef.name;

		//synth's \fadeTime is longestWaitTime. It could probably be removed here,
		//as it will be set eventually in the case of .clear / etc...
		var synthArgs = [\out, synthBus.index, \fadeTime, longestWaitTime];

		//connect each param with specific normBus
		normBusses.keysValuesDo({ | param, normBus |
			synthArgs = synthArgs.add(param);
			synthArgs = synthArgs.add(normBus.busArg);
		});

		//create synth
		synth = AlgaSynth.new(
			defName,
			synthArgs,
			synthGroup
		);
	}

	resetInterpNormDicts {
		interpSynths.clear;
		normSynths.clear;
		interpBusses.clear;
		normBusses.clear;
	}

	//Either retrieve default value from controlName or from args
	getDefaultOrArg { | controlName, param = \in |
		var defaultOrArg = controlName.defaultValue;

		var objArg = objArgs[param];

		//If objArgs has entry, use that one as default instead
		if(objArg != nil, {
			if(objArg.isNumberOrArray, {
				defaultOrArg = objArg;
			}, {
				if(objArg.isAlgaNode, {
					//Schedule connection with the algaNode
					this.makeConnection(objArg, param);
				});
			});
		});

		^defaultOrArg;
	}

	//Check correct array size for scale arguments
	checkScaleParameterSize { | scaleEntry, name, param, paramNumChannels |
		if(scaleEntry.isSequenceableCollection, {
			var scaleEntrySize = scaleEntry.size;
			if(scaleEntry.size != paramNumChannels, {
				("AlgaNode: the " ++ name ++ " entry of the scale parameter has less channels(" ++
					scaleEntrySize ++ ") than param " ++ param ++
					". Wrapping around " ++ paramNumChannels ++ " number of channels."
				).warn;
				^(scaleEntry.reshape(paramNumChannels));
			});
			^scaleEntry;
		}, {
			if(scaleEntry.isNumber, {
				^([scaleEntry].reshape(paramNumChannels));
			}, {
				"AlgaNode: scale entries can only be number or array".error;
				^nil;
			})
		});
	}

	addScaling { | param, sender, scale |
		if(paramsScalings[param] == nil, {
			paramsScalings[param] = IdentityDictionary(2);
			paramsScalings[param][sender] = scale;
		}, {
			paramsScalings[param][sender] = scale;
		});
	}

	removeScaling { | param, sender |
		if(paramsScalings[param] != nil, {
			paramsScalings[param].removeAt(sender);
		});
	}

	//Calculate scale to send to interp synth
	calculateScaling { | param, sender, paramNumChannels, scale |
		if(scale.isNil, { ^nil });

		if(scale.isSequenceableCollection.not, {
			"AlgaNode: the scale parameter must be an array".error;
			^nil
		});

		//just lowMax / hiMax
		if(scale.size == 2, {
			var outArray = Array.newClear(6);
			var highMin = scale[0];
			var highMax = scale[1];
			var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
			var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);

			if((newHighMin.isNil).or(newHighMax.isNil), {
				^nil
			});

			outArray[0] = \highMin; outArray[1] = newHighMin;
			outArray[2] = \highMax; outArray[3] = newHighMax;
			outArray[4] = \useScaling; outArray[5] = 1;

			scale[0] = newHighMin;
			scale[1] = newHighMax;

			this.addScaling(param, sender, scale);

			^outArray;
		}, {
			//all four of the scales
			if(scale.size == 4, {
				var outArray = Array.newClear(10);
				var lowMin = scale[0];
				var lowMax = scale[1];
				var highMin = scale[2];
				var highMax = scale[3];
				var newLowMin = this.checkScaleParameterSize(lowMin, "lowMin", param, paramNumChannels);
				var newLowMax = this.checkScaleParameterSize(lowMax, "lowMax", param, paramNumChannels);
				var newHighMin = this.checkScaleParameterSize(highMin, "highMin", param, paramNumChannels);
				var newHighMax = this.checkScaleParameterSize(highMax, "highMax", param, paramNumChannels);

				if((newLowMin.isNil).or(newHighMin.isNil).or(newLowMax.isNil).or(newHighMax.isNil), {
					^nil
				});

				outArray[0] = \lowMin; outArray[1] = newLowMin;
				outArray[2] = \lowMax; outArray[3] = newLowMax;
				outArray[4] = \highMin; outArray[5] = newHighMin;
				outArray[6] = \highMax; outArray[7] = newHighMax;
				outArray[8] = \useScaling; outArray[9] = 1;

				scale[0] = newLowMin;
				scale[1] = newLowMax;
				scale[2] = newHighMin;
				scale[3] = newHighMax;

				this.addScaling(param, sender, scale);

				^outArray;
			}, {
				("AlgaNode: the scale parameter must be an array of either 2 " ++
					" (hiMin / hiMax) or 4 (lowMin, lowMax, hiMin, hiMax) entries.").error;
				^nil
			});
		});
	}

	//Calculate the array to be used as \indices param for interpSynth
	calculateSenderChansMappingArray { | param, sender, senderChansMapping,
		senderNumChans, paramNumChans, updateParamsChansMapping = true |

		var actualSenderChansMapping = senderChansMapping;

		//Connect with outMapping symbols. Retrieve it from the sender
		if(actualSenderChansMapping.class == Symbol, {
			actualSenderChansMapping = sender.outsMapping[actualSenderChansMapping];
		});

		//Update entry in Dict with the non-modified one (used in .replace then)
		if(updateParamsChansMapping, {
			paramsChansMapping[param][sender] = actualSenderChansMapping;
		});

		//Standard case (perhaps, overkill. This is default of the \indices param anyway)
		if(actualSenderChansMapping == nil, { ^(Array.series(paramNumChans)) });

		if(actualSenderChansMapping.isSequenceableCollection, {
			//Also allow [\out1, \out2] here.
			actualSenderChansMapping.do({ | entry, index |
				if(entry.class == Symbol, {
					actualSenderChansMapping[index] = sender.outsMapping[entry];
				});
			});

			//flatten the array, modulo around the max number of channels and reshape according to param num chans
			^((actualSenderChansMapping.flat % senderNumChans).reshape(paramNumChans));
		}, {
			if(actualSenderChansMapping.isNumber, {
				^(Array.fill(paramNumChans, { actualSenderChansMapping }));
			}, {
				"senderChansMapping must be a number or an array. Using default one.".error;
				^(Array.series(paramNumChans));
			});
		});
	}

	//AlgaNode.new or .replace
	createInterpNormSynths { | replace = false, keepChannelsMapping = false, keepScale = false |
		controlNames.do({ | controlName |
			var normBus;

			var paramName = controlName.name;
			var paramNumChannels = controlName.numChannels;

			var paramRate = controlName.rate;
			var paramDefault = this.getDefaultOrArg(controlName, paramName);

			var noSenders = false;

			normBus = normBusses[paramName];

			//If replace, connect to the pervious bus, not default
			if(replace, {
				var sendersSet = inNodes[paramName];

				//Restoring a connected parameter, being it normal or mix
				if(sendersSet != nil, {
					var onlyEntry = false;

					//if size == 1, index from \default
					if(sendersSet.size == 1, {
						onlyEntry = true;
					});

					sendersSet.do({ | prevSender |
						var interpBus, interpSynth;
						var envBus, envSynth;

						var normSynth;

						var interpSymbol, envSymbol, normSymbol;

						var interpEnvGroup;

						var oldParamsChansMapping = nil;
						var oldParamScale = nil;

						var interpSynthArgs;

						var channelsMapping;
						var scaleArray;

						if(onlyEntry, {
							interpBus = interpBusses[paramName][\default];
							envBus = envBusses[paramName][\default];
						}, {
							//mix param... Also update default node!
							interpBus = AlgaBus(server, paramNumChannels, paramRate);
							envBus = AlgaBus(server, 1, paramRate);

							interpBusses[paramName][prevSender] = interpBus;
							envBusses[paramName][prevSender] = envBus;
						});

						//Use previous entry for the channel mapping, otherwise, nil.
						//nil will generate Array.series(...) in calculateSenderChansMappingArray
						if(keepChannelsMapping, {
							oldParamsChansMapping = paramsChansMapping[paramName][prevSender];
						});

						//Use previous entry for inputs scaling
						if(keepScale, {
							oldParamScale = paramsScalings[paramName][prevSender];
						});

						//overwrite interp symbol considering the senders' num channels!
						interpSymbol = (
							"alga_interp_" ++
							prevSender.rate ++
							prevSender.numChannels ++
							"_" ++
							paramRate ++
							paramNumChannels
						).asSymbol;

						envSymbol = ("alga_env_" ++ paramRate).asSymbol;

						normSymbol = (
							"alga_norm_" ++
							paramRate ++
							paramNumChannels
						).asSymbol;

						//Calculate the array for channelsMapping
						channelsMapping = this.calculateSenderChansMappingArray(
							paramName,
							prevSender,
							oldParamsChansMapping,
							prevSender.numChannels,
							paramNumChannels,
							false
						);

						scaleArray = this.calculateScaling(
							paramName, prevSender,
							paramNumChannels, oldParamScale
						);

						interpSynthArgs = [
							\in, prevSender.synthBus.busArg,
							\env, envBus.busArg,
							\out, interpBus.index,
							\indices, channelsMapping,
							\fadeTime, 0
						];

						//Add scale array to args
						if(scaleArray != nil, {
							scaleArray.do({ | entry |
								interpSynthArgs = interpSynthArgs.add(entry);
							});
						});

						interpEnvGroup = Group(interpGroup);

						envSynth = AlgaSynth(
							envSymbol,
							[\out, envBus.index, \fadeTime, 0],
							interpEnvGroup
						);

						interpSynth = AlgaSynth(
							interpSymbol,
							interpSynthArgs,
							interpEnvGroup
						);

						//Instantiated right away, with no \fadeTime, as it will directly be connected to
						//synth's parameter. Synth will read its params from all the normBusses
						normSynth = AlgaSynth(
							normSymbol,
							[\in, interpBus.busArg, \env, envBus.busArg, \out, normBus.index, \fadeTime, 0],
							normGroup
						);

						if(onlyEntry, {
							//normal param
							interpSynths[paramName][\default] = interpSynth;
							envSynths[paramName][\default] = envSynth;
							normSynths[paramName][\default] = normSynth;
						}, {
							//mix param
							interpSynths[paramName][prevSender] = interpSynth;
							envSynths[paramName][prevSender] = envSynth;
							normSynths[paramName][prevSender] = normSynth;

							//And update the ones in \default if this sender is the currentDefaultNode!!!
							//This is essential, because otherwise interpBusses[paramName][\default]
							//would be a bus reading from nothing!
							if(currentDefaultNodes[paramName] == prevSender, {
								interpBusses[paramName][\default] = interpBus; //previous' \default should be freed
								envBusses[paramName][\default] = envBus;
								interpSynths[paramName][\default] = interpSynth;
								normSynths[paramName][\default] = normSynth;
							});
						});
					});
				}, {
					noSenders = true;
				})
			});

			//interpSynths and normSynths are a IdentityDict of IdentityDicts
			if(replace.not.or(noSenders), {
				//e.g. \alga_env_audio
				var envSymbol = ("alga_env_" ++ paramRate).asSymbol;

				//e.g. \alga_interp_audio1_control1
				var interpSymbol = (
					"alga_interp_" ++
					paramRate ++
					paramNumChannels ++
					"_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				//e.g. \alga_norm_audio1
				var normSymbol = (
					"alga_norm_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				//default interpBus
				var interpBus = interpBusses[paramName][\default];
				var envBus = envBusses[paramName][\default];

				var interpEnvGroup = Group(interpGroup);

				//env synth
				var envSynth = AlgaSynth(
					envSymbol,
					[\out, envBus.index, \fadeTime, 0],
					interpEnvGroup
				);

				//use paramDefault: no replace or no senders in sendersSet
				var interpSynth = AlgaSynth(
					interpSymbol,
					[\in, paramDefault, \env, envBus.busArg, \out, interpBus.index, \fadeTime, 0],
					interpEnvGroup
				);

				//Instantiated right away, with no \fadeTime, as it will directly be connected to
				//synth's parameter. Synth will read its params from all the normBusses
				var normSynth = AlgaSynth(
					normSymbol,
					[\in, interpBus.busArg, \env, envBus.busArg, \out, normBus.index, \fadeTime, 0],
					normGroup
				);

				interpSynths[paramName][\default] = interpSynth;
				envSynths[paramName][\default] = envSynth;
				normSynths[paramName][\default] = normSynth;
			});
		});
	}

	//Create all synths for each param
	createAllSynths { | replace = false, keepChannelsMapping = false, keepScale = false |
		this.createInterpNormSynths(
			replace,
			keepChannelsMapping:keepChannelsMapping,
			keepScale:keepScale
		);

		this.createSynth;
	}

	//Run when <<+ / .replace (on mixed connection) / .replaceMix
	createMixInterpSynthInterpBusBusNormSynthAtParam { | sender, param = \in, replaceMix = false,
		replace = false, senderChansMapping, scale, time |

		//also check sender is not the default!
		var newMixConnection = (
			this.mixParamContainsSender(param, sender).not).and(
			sender != currentDefaultNodes[param]
		);

		var newMixConnectionOrReplaceMix = newMixConnection.or(replaceMix);
		var newMixConnectionOrReplace = newMixConnection.or(replace);

		//Only run if replaceMix (meaning, a mix entry has been explicitly replaced)
		//OR no param/sender combination is present already, otherwise it means it was already connected!
		if(newMixConnectionOrReplaceMix, {
			var controlName;
			var paramNumChannels, paramRate;
			var normSymbol, normBus, normSynth;
			var interpBus, envBus;

			controlName = controlNames[param];
			normBus = normBusses[param];

			paramNumChannels = controlName.numChannels;
			paramRate = controlName.rate;

			normSymbol = (
				"alga_norm_" ++
				paramRate ++
				paramNumChannels
			).asSymbol;

			interpBus = AlgaBus(server, paramNumChannels, paramRate);

			envBus = AlgaBus(server, 1, paramRate);

			normSynth = AlgaSynth.new(
				normSymbol,
				[\in, interpBus.busArg, \env, envBus.busArg, \out, normBus.index, \fadeTime, 0],
				normGroup
			);

			interpBusses[param][sender] = interpBus;
			envBusses[param][sender] = envBus;
			normSynths[param][sender] = normSynth;
		});

		//Make sure to not duplicate something that's already in the mix
		if(newMixConnectionOrReplace.or(newMixConnectionOrReplaceMix), {
			this.createInterpSynthAtParam(sender, param,
				mix:true, newMixConnectionOrReplaceMix:newMixConnectionOrReplaceMix,
				senderChansMapping:senderChansMapping, scale:scale, time:time
			);
		}, {
			//the alga node is already mixed. run replaceMix with itself
			//this is useful in case scale parameter has been changed by user
			"The AlgaNode was already mixed. Running 'replaceMix' with itself instead".warn;
			this.replaceMixInner(param, sender, sender, senderChansMapping, scale, time:time);
		});
	}

	//Used at every <<, >>, <<+, >>+, <|
	createInterpSynthAtParam { | sender, param = \in, mix = false,
		newMixConnectionOrReplaceMix = false, senderChansMapping, scale, time |

		var controlName, paramConnectionTime;
		var paramNumChannels, paramRate;
		var senderNumChannels, senderRate;

		var senderChansMappingToUse;

		var scaleArray;

		var interpSymbol, envSymbol;

		var interpBusAtParam;
		var envBusAtParam;
		var interpBus, interpSynth;
		var envBus, envSynth;

		var interpEnvGroup;

		var interpSynthArgs;

		var senderSym = sender;

		if(mix, {
			if(sender == nil, {
				senderSym = \default;
			}, {
				//create an ad-hoc entry... This won't ever be triggered for now
				//(mix has been already set to false in makeConnection for number/array)
				if(sender.isNumberOrArray, {
					senderSym = (sender.asString ++ "_" ++ UniqueID.next.asString).asSymbol;
				});
			});
		}, {
			senderSym = \default;
		});

		controlName = controlNames[param];
		paramConnectionTime = paramsConnectionTime[param];

		if((controlName.isNil).or(paramConnectionTime.isNil), {
			("Invalid param for interp synth to free: " ++ param).error;
			^this
		});

		//If -1, or invalid, set to global connectionTime
		if(paramConnectionTime < 0, { paramConnectionTime = connectionTime });

		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

		time.asString.error;

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
			"alga_interp_" ++
			senderRate ++
			senderNumChannels ++
			"_" ++
			paramRate ++
			paramNumChannels
		).asSymbol;

		envSymbol = ("alga_env_" ++ paramRate).asSymbol;

		//get interp bus ident dict at specific param
		interpBusAtParam = interpBusses[param];
		if(interpBusAtParam == nil, { ("Invalid interp bus at param " ++ param).error; ^this });

		//Try to get sender one.
		//If not there, get the default one (and assign it to sender for interpBus at param)
		interpBus = interpBusAtParam[senderSym];
		if(interpBus == nil, {
			interpBus = interpBusAtParam[\default];
			if(interpBus == nil, {
				(
					"Invalid interp bus at param " ++
					param ++ " and node " ++ senderSym.asString
				).error;
				^this
			});

			interpBusAtParam[senderSym] = interpBus;
		});

		//get env bus ident dict at specific param
		envBusAtParam = interpBusses[param];
		if(envBusAtParam == nil, { ("Invalid env bus at param " ++ param).error; ^this });

		//Try to get sender one.
		//If not there, get the default one (and assign it to sender for envBus at param)
		envBus = envBusAtParam[senderSym];
		if(envBus == nil, {
			envBus = envBusAtParam[\default];
			if(envBus == nil, {
				(
					"Invalid env bus at param " ++
					param ++ " and node " ++ senderSym.asString
				).error;
				^this
			});

			envBusAtParam[senderSym] = envBus;
		});

		//Array for \indices parameter
		senderChansMappingToUse = this.calculateSenderChansMappingArray(
			param,
			sender, //must be sender! not senderSym!
			senderChansMapping,
			senderNumChannels,
			paramNumChannels,
		);

		//calculate scale array
		scaleArray = this.calculateScaling(param, sender, paramNumChannels, scale);

		//new interp synth, with input connected to sender and output to the interpBus
		//THIS USES connectionTime!!
		if(sender.isAlgaNode, {
			//If mix and replaceMix, spawn a fadeIn synth, which balances out the interpSynth's envelope for normSynth
			if(mix.and(newMixConnectionOrReplaceMix), {
				var fadeInSymbol = ("alga_fadeIn_" ++
					paramRate ++
					paramNumChannels
				).asSymbol;

				AlgaSynth.new(
					fadeInSymbol,
					[
						\out, interpBus.index,
						\fadeTime, time,
					],
					interpGroup
				);
			});

			//read from sender's synth bus!
			interpSynthArgs = [
				\in, sender.synthBus.busArg,
				\env, envBus.busArg,
				\out, interpBus.index,
				\indices, senderChansMappingToUse
			];

			//add scaleArray to args
			if(scaleArray != nil, {
				scaleArray.do({ | entry |
					interpSynthArgs = interpSynthArgs.add(entry);
				});
			});

			interpEnvGroup = Group(interpGroup);

			envSynth = AlgaSynth(
				envSymbol,
				[\out, envBus.index, \fadeTime, time],
				interpEnvGroup
			);

			interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpGroup
			);
		}, {
			//Used in <| AND << with number / array
			//if sender is nil, restore the original default value. This is used in <|
			var paramVal;

			if(sender == nil, {
				paramVal = this.getDefaultOrArg(controlName, param); //either default or provided arg!
			}, {
				//If not nil, check if it's a number or array. Use it if that's the case
				if(sender.isNumberOrArray,  {
					paramVal = sender;
				}, {
					"Invalid paramVal for AlgaNode".error;
					^nil;
				});
			});

			interpSynthArgs = [
				\in, paramVal,
				//\env, envBus.busArg,
				\out, interpBus.index,
				\indices, senderChansMappingToUse
			];

			//add scaleArray to args
			if(scaleArray != nil, {
				scaleArray.do({ | entry |
					interpSynthArgs = interpSynthArgs.add(entry);
				});
			});

			interpEnvGroup = Group(interpGroup);

			envSynth = AlgaSynth(
				envSymbol,
				[\out, envBus.index, \fadeTime, time],
				interpEnvGroup
			);

			interpSynth = AlgaSynth(
				interpSymbol,
				interpSynthArgs,
				interpEnvGroup
			);
		});

		//Add to interpSynths for the param
		interpSynths[param][senderSym] = interpSynth;
		envSynths[param][senderSym] = envSynth;

		//Store current \default (needed when going from mix == true to mix == false)...
		//basically, restoring proper connections after going from <<+ to << or <|
		//This is only activated with << and <<|, and it makes sure of hainvg proper \default nodes
		//The \default entries for interpBusses, interpSynths and normSynths
		//are already taken care of in createInterpNormSynths
		if((senderSym == \default).and(mix == false), {
			currentDefaultNodes[param] = sender;
		});
	}

	//Eventually use this func to free all synths that use \gate and \fadeTime
	freeSynthOnScheduler { | whatSynth, whatFadeTime |
		if(whatSynth.instantiated, {
			whatSynth.set(\gate, 0, \fadeTime, whatFadeTime);
		}, {
			algaScheduler.addAction({ whatSynth.instantiated }, {
				whatSynth.set(\gate, 0, \fadeTime, whatFadeTime);
			});
		});
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
			interpSynths.do({ | interpSynthsAtParam |
				interpSynthsAtParam.do({ | interpSynth |
					interpSynth.set(\gate, 0, \fadeTime, 0);
				});
			});

			normSynths.do({ | normSynthsAtParam |
				normSynthsAtParam.do({ | normSynth |
					normSynth.set(\gate, 0, \fadeTime, 0);
				});
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

				prevInterpSynths.do({ | interpSynthsAtParam |
					interpSynthsAtParam.do({ | interpSynth |
						interpSynth.set(\gate, 0, \fadeTime, 0);
					});
				});

				prevNormSynths.do({ | normSynthsAtParam |
					normSynthsAtParam.do({ | normSynth |
						normSynth.set(\gate, 0, \fadeTime, 0);
					});
				});
			}
		});
	}

	freeAllSynths { | useConnectionTime = true, now = true |
		this.freeInterpNormSynths(useConnectionTime, now);
		this.freeSynth(useConnectionTime, now);
	}

	//Free the entire mix node at specific param.
	freeMixNodeAtParam { | sender, param = \in, paramConnectionTime,
		replace = false, cleanupDicts = false, time |

		var interpSynthAtParam = interpSynths[param][sender];

		if(interpSynthAtParam != nil, {
			//These must be fetched before the addAction (they refer to the current state!)
			var normSynthAtParam = normSynths[param][sender];
			var interpBusAtParam = interpBusses[param][sender];
			var currentDefaultNode = currentDefaultNodes[param];

			//Make sure all of these are scheduled correctly to each other!
			algaScheduler.addAction({ (normSynthAtParam.instantiated).and(interpSynthAtParam.instantiated) }, {
				var notDefaultNode = false;

				//Only run fadeOut and remove normSynth if they are also not the ones that are used for \default.
				//This makes sure that \defauls is kept alive at all times
				if(sender != currentDefaultNode, {
					notDefaultNode = true;
				});

				//calculate temporary time
				time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

				//Only create fadeOut and free normSynth on .replaceMix and .disconnect! (not .replace).
				//Also, don't create it for the default node, as that needs to be kept alive at all times!
				if(notDefaultNode.and(replace.not), {
					var fadeOutSymbol = ("alga_fadeOut_" ++
						interpBusAtParam.rate ++
						(interpBusAtParam.numChannels - 1) //it has one more for env. need to remove that from symbol
					).asSymbol;

					AlgaSynth.new(
						fadeOutSymbol,
						[
							\out, interpBusAtParam.index,
							\fadeTime, time,
						],
						interpGroup
					);

					//This has to be surely instantiated before being freed
					normSynthAtParam.set(\gate, 0, \fadeTime, time);
				});

				//This has to be surely instantiated before being freed
				interpSynthAtParam.set(\gate, 0, \fadeTime, time);
			});
		});

		//On a .disconnect / .replaceMix, remove the entry
		if(cleanupDicts, {
			interpSynths[param].removeAt(sender);
			interpBusses[param].removeAt(sender);
			normSynths[param].removeAt(sender);
		});
	}

	//Free interpSynth at param. This is also used in .replace for both mix entries and normal ones
	//THIS USES connectionTime!!
	freeInterpSynthAtParam { | sender, param = \in, mix = false, replace = false, time |
		var interpSynthsAtParam = interpSynths[param];
		var paramConnectionTime = paramsConnectionTime[param];

		//If -1, or invalid, set to global connectionTime
		if((paramConnectionTime < 0).or(paramConnectionTime == nil), { paramConnectionTime = connectionTime });

		//Free them all (check if there were mix entries).
		//sender == nil comes from <|
		//mix == false comes from <<
		//mix == true comes from <<+, .replaceMix, .replace, .disconnect
		if((sender == nil).or(mix == false), {
			//If interpSynthsAtParam length is more than one, the param has mix entries. Fade them all out.
			if(interpSynthsAtParam.size > 1, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam  |
					if(interpSender != \default, { // ignore \default !
						if(replace, {
							//on .replace of a mix node, just free that one (without fadeOut)
							this.freeMixNodeAtParam(interpSender, param,
								replace:true, cleanupDicts:false
							);
						}, {
							//.disconnect
							this.freeMixNodeAtParam(interpSender, param,
								paramConnectionTime:paramConnectionTime,
								replace:false, cleanupDicts:true, time:time
							);
						});
					});
				});
			}, {
				//calculate temporary time
				time = this.calculateTemporaryLongestWaitTime(time, paramConnectionTime);

				time.asString.warn;

				//Just one entry in the dict (\default), just free that interp synth!
				interpSynthsAtParam.do({ | interpSynthAtParam |
					interpSynthAtParam.nodeID.asString.error;
					interpSynthAtParam.set(\gate, 0, \fadeTime, time);
				});
			});
		}, {
			//mix == true, only free the one (disconnect function)
			if(replace, {
				//on .replace of a mix node, just free that one (without fadeOut)
				this.freeMixNodeAtParam(sender, param,
					paramConnectionTime:paramConnectionTime,
					replace:true, cleanupDicts:false, time:time
				);
			}, {
				//.disconnect
				this.freeMixNodeAtParam(sender, param,
					paramConnectionTime:paramConnectionTime,
					replace:false, cleanupDicts:true, time:time
				);
			});
		});
	}

	//param -> IdentitySet[AlgaNode, AlgaNode, ...]
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
			//Empty entry OR not doing mixing, create new IdentitySet. Otherwise, add to existing
			if((inNodes[param] == nil).or(mix.not), {
				inNodes[param] = IdentitySet[sender];
			}, {
				inNodes[param].add(sender);
			})
		});
	}

	//AlgaNode -> IdentitySet[param, param, ...]
	addOutNode { | receiver, param = \in |
		//Empty entry, create IdentitySet. Otherwise, add to existing
		if(outNodes[receiver] == nil, {
			outNodes[receiver] = IdentitySet[param];
		}, {
			outNodes[receiver].add(param);
		});
	}

	//add entries to the inNodes / outNodes / connectionTimeOutNodes of the two AlgaNodes
	addInOutNodesDict { | sender, param = \in, mix = false |
		//This will replace the entries on new connection (as mix == false)
		this.addInNode(sender, param, mix);

		//This will add the entries to the existing IdentitySet, or create a new one
		if(sender.isAlgaNode, {
			sender.addOutNode(this, param);

			//Add to connectionTimeOutNodes and recalculate longestConnectionTime
			sender.connectionTimeOutNodes[this] = this.connectionTime;
			sender.calculateLongestConnectionTime(this.connectionTime);
		});
	}

	removeInOutNodeAtParam { | sender, param = \in |
		//Just remove one param from sender's set at this entry
		sender.outNodes[this].remove(param);

		//If IdentitySet is now empty, remove it entirely
		if(sender.outNodes[this].size == 0, {
			sender.outNodes.removeAt(this);
		});

		//Remove the specific param / sender combination from inNodes
		inNodes[param].remove(sender);

		//If IdentitySet is now empty, remove it entirely
		if(inNodes[param].size == 0, {
			inNodes.removeAt(param);
		});

		//Recalculate longestConnectionTime too...
		//SHOULD THIS BE DONE AFTER THE SYNTHS ARE CREATED???
		//(Right now, this happens before creating new synths)
		sender.connectionTimeOutNodes[this] = 0;
		sender.calculateLongestConnectionTime(0);
	}

	//Remove entries from inNodes / outNodes / connectionTimeOutNodes for all involved nodes
	removeInOutNodesDict { | previousSender = nil, param = \in |
		var previousSenders = inNodes[param];
		if(previousSenders == nil, { ( "No previous connection enstablished at param: " ++ param).error; ^this; });

		previousSenders.do({ | sender |
			var sendersParamsSet = sender.outNodes[this];
			if(sendersParamsSet != nil, {
				//no previousSender specified: remove them all!
				if(previousSender == nil, {
					this.removeInOutNodeAtParam(sender, param);
				}, {
					//If specified previousSender, only remove that one (in mixing scenarios)
					if(sender == previousSender, {
						this.removeInOutNodeAtParam(sender, param);
					})
				})
			})
		});
	}

	//Clear the dicts
	resetInOutNodesDicts {
		if(toBeCleared, {
			inNodes.clear;
			outNodes.clear;
		});
	}

	//New interp connection at specific parameter
	newInterpConnectionAtParam { | sender, param = \in, replace = false,
		senderChansMapping, scale, time |

		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to create a new interp synth for: " ++ param).error;
			^this;
		});

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:false);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if(replace.not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//Free previous interp synth(s) (fades out)
		this.freeInterpSynthAtParam(sender, param, time:time);

		//Spawn new interp synth (fades in)
		this.createInterpSynthAtParam(sender, param,
			senderChansMapping:senderChansMapping, scale:scale, time:time
		);
	}

	//New mix connection at specific parameter
	newMixConnectionAtParam { | sender, param = \in, replace = false,
		replaceMix = false, senderChansMapping, scale, time |

		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to create a new interp synth for: " ++ param).error;
			^this;
		});

		//Add proper inNodes / outNodes / connectionTimeOutNodes
		this.addInOutNodesDict(sender, param, mix:true);

		//Re-order groups
		//Actually reorder the block's nodes ONLY if not running .replace
		//(no need there, they are already ordered, and it also avoids a lot of problems
		//with feedback connections)
		if((replace.not).and(replaceMix).not, {
			AlgaBlocksDict.createNewBlockIfNeeded(this, sender);
		});

		//If replaceMix = true, the call comes from replaceMix, which already calls freeInterpSynthAtParam!
		if(replace, {
			this.freeInterpSynthAtParam(sender, param, mix:true, replace:true, time:time);
		});

		//Spawn new interp mix node
		this.createMixInterpSynthInterpBusBusNormSynthAtParam(sender, param,
			replaceMix:replaceMix, replace:replace,
			senderChansMapping:senderChansMapping, scale:scale, time:time
		);
	}

	//Used in <| and replaceMix
	removeInterpConnectionAtParam { | previousSender = nil, param = \in, time |
		var controlName = controlNames[param];
		if(controlName == nil, {
			("Invalid param to reset: " ++ param).error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes
		this.removeInOutNodesDict(previousSender, param);

		//Re-order groups shouldn't be needed when removing connections

		//Free previous interp synth (fades out)
		this.freeInterpSynthAtParam(previousSender, param, time:time);

		//Create new interp synth with default value (or the one supplied with args at start) (fades in)
		this.createInterpSynthAtParam(nil, param, time:time);
	}

	//Cleans up all Dicts at param, leaving the \default entry only
	cleanupMixBussesAndSynths { | param |
		var interpBusAtParam = interpBusses[param];
		if(interpBusAtParam.size > 1, {
			var interpSynthAtParam = interpSynths[param];
			var normSynthAtParam = normSynths[param];
			interpBusAtParam.keysValuesDo({ | key, val |
				if(key != \default, {
					interpBusAtParam.removeAt(key);
					interpSynthAtParam.removeAt(key);
					normSynthAtParam.removeAt(key);
				});
			});
		});
	}

	//First time a mix parameter is added: make sure to add what was the \default to
	//all the mix dictionaries. This is actually only needed once, on the first time of <<+
	//so that the previous connection is considered part of the mix
	moveDefaultNodeToMix { | param = \in, sender |
		var currentDefaultNodeAtParam = currentDefaultNodes[param];
		if(currentDefaultNodeAtParam.isAlgaNode, { //only if default is AlgaNode (not number or array)
			if(currentDefaultNodeAtParam != sender, {
				var interpBussesAtParam = interpBusses[param];
				var interpSynthsAtParam = interpSynths[param];
				var normSynthsAtParam = normSynths[param];

				//If there was no entry, add it!
				if(interpBussesAtParam[currentDefaultNodeAtParam] == nil, {
					interpBussesAtParam[currentDefaultNodeAtParam] = interpBussesAtParam[\default];
				});

				if(interpSynthsAtParam[currentDefaultNodeAtParam] == nil, {
					interpSynthsAtParam[currentDefaultNodeAtParam] = interpSynthsAtParam[\default];
				});

				if(normSynthsAtParam[currentDefaultNodeAtParam] == nil, {
					normSynthsAtParam[currentDefaultNodeAtParam] = normSynthsAtParam[\default];
				});
			});
		});
	}

	//implements receiver <<.param sender
	makeConnectionInner { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time |

		if((sender.isAlgaNode.not).and(sender.isNumberOrArray.not), {
			"Can't connect to something that's not an AlgaNode, a Number or an Array".error;
			^this
		});

		//Can't connect AlgaNode to itself
		if(this === sender, { "Can't connect an AlgaNode to itself".error; ^this });

		if(mix, {
			var currentDefaultNodeAtParam = currentDefaultNodes[param];

			//trying to <<+ instead of << on first connection
			if((currentDefaultNodeAtParam == nil), {
				mix = false;
			});

			//can't add to a num. just replace it
			if(currentDefaultNodeAtParam.isNumberOrArray, {
				("Trying to add to a non-AlgaNode: " ++ currentDefaultNodeAtParam.asString ++ ". Replacing it.").warn;
				mix = false;
			});

			//can't <<+ with numbers or arrays. There would be no way to track them, unless DC.kr/ar
			if((sender == nil).or(sender.isNumberOrArray), {
				("Mixing only works for explicit AlgaNodes.").error;
				^this;
			});

			//trying to run replaceMix / mixFrom / mixTo when sender is the only entry!
			if(inNodes[param].size == 1, {
				if(inNodes[param].findMatch(sender) != nil, {
					"AlgaNode was the only entry. Running makeConnection instead".warn;
					mix = false;
				});
			});
		});

		//need to re-check as mix might have changed!
		if(mix, {
			//Make sure the \default node is also added to mix
			this.moveDefaultNodeToMix(param, sender);

			//Either new one <<+ / .replaceMix OR .replace
			this.newMixConnectionAtParam(sender, param,
				replace:replace, replaceMix:replaceMix,
				senderChansMapping:senderChansMapping,
				scale:scale, time:time
			)
		}, {
			//mix == false, clean everything up

			//Connect interpSynth to the sender's synthBus
			this.newInterpConnectionAtParam(sender, param,
				replace:replace, senderChansMapping:senderChansMapping,
				scale:scale, time:time
			);

			//Cleanup interpBusses / interpSynths / normSynths from previous mix, leaving \default only
			this.cleanupMixBussesAndSynths(param);
		});
	}

	//Wrapper for scheduler
	makeConnection { | sender, param = \in, replace = false, mix = false,
		replaceMix = false, senderChansMapping, scale, time |

		if(this.cleared.not.and(sender.cleared.not).and(sender.toBeCleared.not), {
			algaScheduler.addAction({ (this.instantiated).and(sender.instantiated) }, {
				this.makeConnectionInner(sender, param, replace, mix,
					replaceMix, senderChansMapping, scale, time:time
				);
			});
		}, {
			"AlgaNode: can't makeConnection, sender has been cleared".error;
		});
	}

	from { | sender, param = \in, inChans, scale, time |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, senderChansMapping:inChans,
				scale:scale, time:time
			);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, senderChansMapping:inChans,
					scale:scale, time:time
				);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//arg is the sender. it can also be a number / array to set individual values
	<< { | sender, param = \in |
		this.from(sender: sender, param: param);
	}

	to { | receiver, param = \in, outChans, scale, time |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, senderChansMapping:outChans,
				scale:scale, time:time
			);
		}, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
		});
	}

	//arg is the receiver
	>> { | receiver, param = \in |
		this.to(receiver: receiver, param: param);
	}

	mixFrom { | sender, param = \in, inChans, scale, time |
		if(sender.isAlgaNode, {
			if(this.server != sender.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			this.makeConnection(sender, param, mix:true, senderChansMapping:inChans,
				scale:scale, time:time
			);
		}, {
			if(sender.isNumberOrArray, {
				this.makeConnection(sender, param, mix:true, senderChansMapping:inChans,
					scale:scale, time:time
				);
			}, {
				("Trying to enstablish a connection from an invalid AlgaNode: " ++ sender).error;
			});
		});
	}

	//add to already running nodes (mix)
	<<+ { | sender, param = \in |
		this.mixFrom(sender: sender, param: param);
	}

	mixTo { | receiver, param = \in, outChans, scale, time |
		if(receiver.isAlgaNode, {
			if(this.server != receiver.server, {
				("Trying to enstablish a connection between two AlgaNodes on different servers").error;
				^this;
			});
			receiver.makeConnection(this, param, mix:true, senderChansMapping:outChans,
				scale:scale, time:time
			);
		}, {
			("Trying to enstablish a connection to an invalid AlgaNode: " ++ receiver).error;
		});
	}

	//add to already running nodes (mix)
	>>+ { | receiver, param = \in |
		this.mixTo(receiver: receiver, param: param);
	}

	//disconnect + makeConnection, very easy
	replaceMixInner { | param = \in, previousSender, newSender, inChans, scale, time |
		this.disconnectInner(param, previousSender, true, time:time);
		this.makeConnectionInner(newSender, param,
			replace:false, mix:true, replaceMix:true,
			senderChansMapping:inChans, scale:scale, time:time
		);
	}

	//Replace a mix entry at param... Practically just freeing the old one and triggering the new one.
	//This will be useful in the future if wanting to implement some kind of system to retrieve individual
	//mix entries (like, \in1, \in2). No need it for now
	replaceMix { | param = \in, previousSender, newSender, inChans, scale, time |
		if(newSender.isAlgaNode.not, {
			(newSender.asString) ++ " is not an AlgaNode".error;
			^this;
		});

		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated).and(newSender.instantiated) }, {
			var validPreviousSender = true;

			//if not contained, it's invalid.
			if(this.mixParamContainsSender(param, previousSender).not, {
				(previousSender.asString ++ " was not present in the mix for param " ++ param.asString).error;
				validPreviousSender = false;
			});

			if(validPreviousSender, {
				this.replaceMixInner(param, previousSender, newSender, inChans, scale, time);
			});
		});
	}

	resetParamInner { | param = \in, previousSender = nil, time |
		//Also remove inNodes / outNodes / connectionTimeOutNodes
		if(previousSender != nil, {
			if(previousSender.isAlgaNode, {
				this.removeInterpConnectionAtParam(previousSender, param, time:time);
			}, {
				("Trying to remove a connection to an invalid AlgaNode: " ++ previousSender).error;
			})
		}, {
			this.removeInterpConnectionAtParam(nil, param, time:time);
		})
	}

	resetParam { | param = \in, previousSender = nil, time |
		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated) }, {
			this.resetParamInner(param, previousSender, time:time);
		});
	}

	//resets to the default value in controlNames
	//OR, if provided, to the value of the original args that were used to create the node
	//previousSender is used in case of mixing, to only remove that one
	<| { | param = \in, previousSender = nil |
		this.resetParam(param, previousSender);
	}

	//On .replace on an already running mix connection
	replaceMixConnectionInner { | param = \in, sender, senderChansMapping, scale, time |
		this.makeConnectionInner(sender, param,
			replace:true, mix:true, replaceMix:false,
			senderChansMapping:senderChansMapping,
			scale:scale, time:time
		);
	}

	//On .replace on an already running mix connection
	replaceMixConnection { | param = \in, sender, senderChansMapping, scale, time |
		algaScheduler.addAction({ (this.instantiated).and(sender.instantiated) }, {
			this.replaceMixConnectionInner(param, sender, senderChansMapping, scale, time);
		});
	}

	//replace connections FROM this
	replaceConnections { | keepChannelsMapping = true, keepScale = true, time |
		//inNodes are already handled in dispatchNode(replace:true)

		//outNodes. Remake connections that were in place with receivers.
		//This will effectively trigger interpolation process.
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				var oldParamsChansMapping = nil;
				var oldScale = nil;

				//Restore old channels mapping! It can either be a symbol, number or array here
				if(keepChannelsMapping, { oldParamsChansMapping = receiver.paramsChansMapping[param][this] });

				//Restore old scale mapping!
				if(keepScale, { oldScale = receiver.paramsScalings[param][this] });

				//If it was a mixer connection, use replaceMixConnection
				if(receiver.mixParamContainsSender(param, this), {
					//use the scheduler version! don't know if receiver and this are both instantiated
					receiver.replaceMixConnection(param, this,
						senderChansMapping:oldParamsChansMapping,
						scale:oldScale, time:time
					);
				}, {
					//use the scheduler version! don't know if receiver and this are both instantiated
					//Normal connection, use makeConnection to re-enstablish it
					receiver.makeConnection(this, param,
						replace:true, senderChansMapping:oldParamsChansMapping,
						scale:oldScale, time:time
					);
				});
			});
		});
	}

	replaceInner { | obj, args, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		outsMapping, keepInScale = true, keepOutScale = true, time |

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

		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, time);

		//This doesn't work with feedbacks, as synths would be freed slightly before
		//The new ones finish the rise, generating click. These should be freed
		//When the new synths/busses are surely instantiated on the server!
		//The cheap solution that it's in place now is to wait 0.5 longer than longestConnectionTime...
		//Work out a better solution!
		this.freeAllSynths(false, false);
		this.freeAllBusses;

		//RESET DICT ENTRIES? SHOULD IT BE DONE SOMEWHERE ELSE? SHOULD IT BE DONE AT ALL?
		this.resetInterpNormDicts;

		//New one
		//Just pass the entry, not the whole thingy
		this.dispatchNode(obj, args,
			initGroups:initGroups,
			replace:true,
			keepChannelsMapping:keepChannelsMappingIn, outsMapping:outsMapping,
			keepScale:keepInScale
		);

		//Re-enstablish connections that were already in place
		this.replaceConnections(
			keepChannelsMapping:keepChannelsMappingOut,
			keepScale:keepOutScale,
			time:time
		);

		//If node was playing, or .replace has been called while .stop / .clear, play again
		if(wasPlaying.or(beingStopped), {
			this.play;
		})
	}

	//Keep min max ??

	//replace content of the node, re-making all the connections.
	//If this was connected to a number / array, should I restore that value too or keep the new one?
	replace { | obj, args, keepChannelsMappingIn = true, keepChannelsMappingOut = true,
		outsMapping, keepInScale = true, keepOutScale = true, time |

		algaScheduler.addAction({ this.instantiated }, {
			this.replaceInner(obj, args, keepChannelsMappingIn,
				keepChannelsMappingOut, outsMapping,
				keepInScale, keepOutScale, time:time
			);
		});

		//Not cleared
		cleared = false;
	}

	//Basically, this checks if the current sender that is being disconnected was the \default node.
	//if it is, it switch the default node with the next available
	checkForUpdateToDefaultNodeAtParam { | param = \in, previousSender |
		//If disconnecting the one that \default is assigned to, it must be switched to another one first!!
		if(currentDefaultNodes[param] == previousSender, {
			var newDefaultNode;

			//Find another one (the first one available)
			newDefaultNode = block ({ | break |
				inNodes[param].do({ | inNode |
					if(inNode != previousSender, {
						break.(inNode); //break returns the argument
					});
				});
			});

			//Update default entries
			if(newDefaultNode != nil, {
				currentDefaultNodes[param] = newDefaultNode;
				interpSynths[param][\default] = interpSynths[param][newDefaultNode];
				normSynths[param][\default] = normSynths[param][newDefaultNode];
				interpBusses[param][\default] = interpBusses[param][newDefaultNode];
			});
		});
	}

	//Remove individual mix entries at param (called from replaceMix too)
	disconnectInner { | param = \in, previousSender, replaceMix = false, time |
		if(this.mixParamContainsSender(param, previousSender).not, {
			(previousSender.asString ++ " was not present in the mix for param " ++ param.asString).error;
			^this;
		});

		//Remove inNodes / outNodes / connectionTimeOutNodes for previousSender
		this.removeInOutNodesDict(previousSender, param);

		//check if \default node needs updating
		this.checkForUpdateToDefaultNodeAtParam(param, previousSender);

		if(replaceMix.not, {
			var interpSynthsAtParam;

			this.freeInterpSynthAtParam(previousSender, param, true, time:time);

			//retrieve the updated ones
			interpSynthsAtParam = interpSynths[param];

			//If length is now 2, it means it's just one mixer AND the \default node left in the dicts.
			//Assign the node to \default and remove the previous mixer.
			//Should I retrieve inNodes.size == 1 instead?
			if(interpSynthsAtParam.size == 2, {
				interpSynthsAtParam.keysValuesDo({ | interpSender, interpSynthAtParam |
					if(interpSender != \default, {
						var normSynthsAtParam = normSynths[param];
						var interpBussesAtParam = interpBusses[param];

						//leave only \default
						interpSynthsAtParam[\default] = interpSynthAtParam;
						interpBussesAtParam[\default] = interpBussesAtParam[interpSender];
						normSynthsAtParam[\default]   = normSynthsAtParam[interpSender];

						interpSynthsAtParam.removeAt(interpSender);
						interpBussesAtParam.removeAt(interpSender);
						normSynthsAtParam.removeAt(interpSender);
					});
				});
			});
		}, {
			//Just free if replaceMix == true
			this.freeInterpSynthAtParam(previousSender, param, true, time:time);
		});
	}

	//Remove individual mix entries at param
	disconnect { | param = \in, previousSender, time |
		if(previousSender.isAlgaNode.not, {
			(previousSender.asString) ++ " is not an AlgaNode".error;
			^this;
		});

		//If it wasn't a mix param, but the only entry, run <| instead
		if(inNodes[param].size == 1, {
			if(inNodes[param].findMatch(previousSender) != nil, {
				"AlgaNode was the only entry. Running <| instead".warn;
				^this.resetParam(param, previousSender, time:time);
			});
		});

		algaScheduler.addAction({ (this.instantiated).and(previousSender.instantiated) }, {
			this.disconnectInner(param, previousSender, time:time);
		});
	}

	//alias for disconnect: remove a mix entry
	removeMix { | param = \in, previousSender |
		this.disconnect(param, previousSender);
	}

	//Find out if specific param / sender combination is in the mix
	mixParamContainsSender { | param = \in, sender |
		^(interpSynths[param][sender] != nil)
		//Or this?
		//^(inNodes[param].findMatch(sender) != nil);
	}

	//When clear, run disconnections to nodes connected to this
	removeConnectionFromReceivers { | time |
		outNodes.keysValuesDo({ | receiver, paramsSet |
			paramsSet.do({ | param |
				//If mixer param, just disconnect the entry connected to this
				if(receiver.mixParamContainsSender(param, this), {
					receiver.disconnect(param, this, time:time);
				}, {
					//no mixer param, just run the disconnect to restore defaults
					receiver.resetParam(param, nil, time:time);
				});
			});
		});
	}

	clearInner { | time |
		//calc temporary time
		time = this.calculateTemporaryLongestWaitTime(time, playTime);

		//If synth had connections, run <| (or disconnect, if mixer) on the receivers
		this.removeConnectionFromReceivers(time);

		//This could be overwritten if .replace is called
		toBeCleared = true;

		//Stop playing (if it was playing at all)
		this.stopInner(time, isClear:true);

		fork {
			//Wait time before clearing groups and busses...
			(longestWaitTime + 1.0).wait;

			//this.freeInterpNormSynths(false, true);
			this.freeAllGroups(true); //I can just remove the groups, as they contain the synths
			this.freeAllBusses(true);

			//Reset all instance variables
			this.resetSynth;
			this.resetInterpNormSynths;
			this.resetGroups;
			this.resetInOutNodesDicts;

			cleared = true;
		}
	}

	clear { | time, interpTime |
		algaScheduler.addAction({ this.instantiated }, {
			this.clearInner(time, interpTime);
		});
	}

	//All synths must be instantiated (including interpolators and normalizers)
	instantiated {
		if(synth == nil, { ^false });

		interpSynths.do({ | interpSynthsAtParam |
			interpSynthsAtParam.do({ | interpSynthAtParam |
				if(interpSynthAtParam.instantiated.not, { ^false });
			});
		});

		normSynths.do({ | normSynthsAtParam |
			normSynthsAtParam.do({ | normSynthAtParam |
				if(normSynthAtParam.instantiated.not, { ^false });
			});
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

	//Number plays those number of channels sequentially
	//Array selects specific output
	createPlaySynth { | time, channelsToPlay |
		if((isPlaying.not).or(beingStopped), {
			var actualNumChannels, playSynthSymbol;

			if(rate == \control, { "Cannot play a kr AlgaNode".error; ^nil; });

			if(channelsToPlay != nil, {
				if(channelsToPlay.isSequenceableCollection, {
					var channelsToPlaySize = channelsToPlay.size;
					if(channelsToPlaySize < numChannels, {
						actualNumChannels = channelsToPlaySize;
					}, {
						actualNumChannels = numChannels;
					});
				}, {
					if(channelsToPlay < numChannels, {
						if(channelsToPlay < 1, { channelsToPlay = 1 });
						if(channelsToPlay > AlgaStartup.algaMaxIO, { channelsToPlay = AlgaStartup.algaMaxIO });
						actualNumChannels = channelsToPlay;
					}, {
						actualNumChannels = numChannels;
					});
				})
			}, {
				actualNumChannels = numChannels
			});

			playSynthSymbol = ("alga_play_" ++ numChannels ++ "_" ++ actualNumChannels).asSymbol;

			time = this.calculateTemporaryLongestWaitTime(time, playTime);

			if(channelsToPlay.isSequenceableCollection, {
				//Wrap around the indices entries (or delete out of bounds???)
				channelsToPlay = channelsToPlay % numChannels;

				playSynth = Synth(
					playSynthSymbol,
					[\in, synthBus.busArg, \indices, channelsToPlay, \gate, 1, \fadeTime, time],
					playGroup
				);
			}, {
				playSynth = Synth(
					playSynthSymbol,
					[\in, synthBus.busArg, \gate, 1, \fadeTime, time],
					playGroup
				);
			});

			isPlaying = true;
			beingStopped = false;
		})
	}

	freePlaySynth { | time, isClear |
		if(isPlaying, {
			if(isClear.not, {
				//time has already been calculated if isClear == true
				time = this.calculateTemporaryLongestWaitTime(time, playTime);
			});
			playSynth.set(\gate, 0, \fadeTime, time);
			isPlaying = false;
			beingStopped = true;
		})
	}

	playInner { | time, channelsToPlay |
		this.createPlaySynth(time, channelsToPlay);
	}

	//Add option for fade time here!
	play { | time, channelsToPlay |
		algaScheduler.addAction({ this.instantiated }, {
			this.playInner(time, channelsToPlay);
		});
	}

	stopInner { | time, isClear = false |
		this.freePlaySynth(time, isClear);
	}

	//Add option for fade time here!
	stop { | time |
		algaScheduler.addAction({ this.instantiated }, {
			this.stopInner(time);
		});
	}

	isAlgaNode { ^true }

	debug {
		"connectionTime:".postln;
		("\t" ++ connectionTime.asString).postln;
		"paramsConnectionTime".postln;
		("\t" ++ paramsConnectionTime.asString).postln;
		"longestWaitTime:".postln;
		("\t" ++ longestWaitTime.asString).postln;
		"controlNames".postln;
		("\t" ++ controlNames.asString).postln;
		"inNodes:".postln;
		("\t" ++ inNodes.asString).postln;
		"outNodes:".postln;
		("\t" ++ outNodes.asString).postln;
		"outs:".postln;
		("\t" ++ outs.asString).postln;
		"paramsChansMapping:".postln;
		("\t" ++ paramsChansMapping.asString).postln;
		"interpSynths:".postln;
		("\t" ++ interpSynths.asString).postln;
		"interpBusses:".postln;
		("\t" ++ interpBusses.asString).postln;
		"normSynths:".postln;
		("\t" ++ normSynths.asString).postln;
		"normBusses:".postln;
		("\t" ++ normBusses.asString).postln;
		"currentDefaultNodes:".postln;
		("\t" ++ currentDefaultNodes.asString).postln;
	}
}

//Alias
AN : AlgaNode {}