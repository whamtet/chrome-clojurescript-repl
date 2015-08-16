var container = $('<div class="console">');
$('body').append(container);

var controller = container.console({
  promptLabel: 'cljs.user=> ',
  commandValidate:function(line){
    if (line == "") return false;
    else return true;
  },
  commandHandle:planck.core.read_eval_print,
//  commandHandle: self_compile.core.cb,
  autofocus:true,
  animateScroll:true,
  notifyPush: planck.core.notify_push,
  history: planck.core.get_history()
});



var attributes = function(element) {
  out = []
  for (var i = 0; i < element.attributes.length; i++) {
    var x = element.attributes[i]
    out.push([x.nodeName, x.nodeValue])
  }
  return out
}
