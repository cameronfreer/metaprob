
all:
	bash parse-all.sh

lein:
	wget "https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein"
	chmod +x lein
	mv lein ~/bin
	lein

# General rule for converting a .vnts (metaprob) file to a .trace file
%.trace: %.vnts
	../metaprob/pythenv.sh python python/transcribe.py -f $< $@.new
	mv -f $@.new $@

# General rule for converting a .trace file to a .clj file
%.clj: %.trace .lein_classpath
	java -cp `cat .lein_classpath` dontknow.main $< $@.new
	mv -f $@.new $@

# This is a trick for running a clojure program directly, without
# lein, thus cutting the number of Java VM startups in half.
# I probably got this from stackoverflow.
.lein_classpath:
	lein classpath > $@

