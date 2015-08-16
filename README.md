# chrome-clojurescript-repl

Clojurescript Repl for Chrome Developer Tools.  Uses Clojurescript 1.7.0 to self compile.  Available on the [Chrome Webstore](https://chrome.google.com/webstore/detail/clojurescript-repl-self-h/clcbfdlgoilmcnnlaglgbjibfimlacdn).

## Setup

To auto build your project in dev mode:

    lein run -m self-compile.watch

Then load the extension in Google Chrome.  Go to chrome://extensions, check developer mode and click load unpacked extension.

## License

Copyright © 2015 Matthew Molloy

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
