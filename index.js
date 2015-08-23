var container = $('<div class="console">');
$('#repl').console({
  promptLabel: 'cljs.user=> ',
  commandValidate:function(line){
    if (line == "") return false;
    else return true;
  },
  commandHandle:planck.core.read_eval_print,
  autofocus:true,
  animateScroll:true,
  notifyPush: planck.core.notify_push,
  history: planck.core.get_history()
});

function do_something(msg) {
  document.getElementById('cb').checked = Boolean(msg)
}

$('#cb').click(function() {
//  respond('click');
  respond(document.getElementById('cb').checked)
})
