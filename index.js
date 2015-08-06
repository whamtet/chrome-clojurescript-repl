var container = $('<div class="console">');
$('body').append(container);

var controller = container.console({
  promptLabel: 'cljs.user=> ',
  commandValidate:function(line){
    if (line == "") return false;
    else return true;
  },
  commandHandle:self_compile.core.cb,
  autofocus:true,
  animateScroll:true,
  promptHistory:true,
});
