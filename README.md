# clojure-tetris

A simple Tetris clone using Clojure and Swing. Created whilst reading Programming Clojure, by Stuart Halloway and Aaron Bedra. Wanted to try out records and stuff - in retrospect, it would've been much cleaner without them. Also, the use of state could probably be avoided (although using Clojure's ref types is awesome). Modelling the game using sets was very natural, really liked it.

Things to fix: 
* Perhaps make a more sophisticated handler for when the JFrame is closed, for when working with the repl.
* Make it more visually pleasing..? Naah...  :)

Made in April 2012.

## Usage

Start it using -main, or simply through the command line if you've built the jar. Normal controls: arrow keys to move blocks, space to turn them, enter to pause the game.

## License

Copyright © 2012 Marcus Magnusson

Distributed under the Eclipse Public License, the same as Clojure.
