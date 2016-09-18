# wi4mu

A simple Web Interface for MU.

This code has been written as an exercise to learn ClojureScript (the
best language for the browser there is), Reagent and React.  It is not
meant to be useful beyond that.

If you need a full and neat interface to MU, you should already know
that MU comes, out of the box, with mu4e, which has everything you
need and some.

```sh
$ lein uberjar
$ java -cp target/wi4mu.jar clojure.main -m wi4mu.server
```

Then visit `http://localhost:10555`.  Or you can specify a different
port on the command line:

```sh
$ java -cp target/wi4mu.jar clojure.main -m wi4mu.server 13337
```

and browse `http://localhost:13337` instead.

Or read the following...


## Development

Open a terminal and type `lein repl` to start a Clojure REPL
(interactive prompt).

In the REPL, type

```clojure
(run)
(browser-repl)
```

The call to `(run)` starts the Figwheel server at port 3449, which takes care of
live reloading ClojureScript code and CSS. Figwheel's server will also act as
your app server, so requests are correctly forwarded to the http-handler you
define.

Running `(browser-repl)` starts the Figwheel ClojureScript REPL. Evaluating
expressions here will only work once you've loaded the page, so the browser can
connect to Figwheel.

When you see the line `Successfully compiled "resources/public/app.js" in 21.36
seconds.`, you're ready to go. Browse to `http://localhost:3449` and enjoy.

**Attention: It is not needed to run `lein figwheel` separately. Instead we
launch Figwheel directly from the REPL**

## Trying it out

If all is well you now have a browser window saying showing a input
box and a search button, and a REPL prompt that looks like
`cljs.user=>`.

### Emacs/CIDER

CIDER is able to start both a Clojure and a ClojureScript REPL simultaneously,
so you can interact both with the browser, and with the server. The command to
do this is `M-x cider-jack-in-clojurescript`.

We need to tell CIDER how to start a browser-connected Figwheel REPL though,
otherwise it will use a JavaScript engine provided by the JVM, and you won't be
able to interact with your running app.

Put this in your Emacs configuration (`~/.emacs.d/init.el` or `~/.emacs`)

``` emacs-lisp
(setq cider-cljs-lein-repl
      "(do (user/run)
           (user/browser-repl))")
```

Now `M-x cider-jack-in-clojurescript` (shortcut: `C-c M-J`, that's a capital
"J", so `Meta-Shift-j`), point your browser at `http://localhost:3449`, and
you're good to go.

## License

Copyright © 2016 Walter C. Pelissero <walter@pelissero.de>

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.14.0 (66af6f40).
