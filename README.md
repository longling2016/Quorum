#pay attention!

##Let's follow the standard as below:
 - the crash rate is expressed as an int n, which means randomly create a number as rand.nextInt(n), crash rate for this moment is 1/n.
 - please do not use the message command "**read**", this is saved for monitor read operation communication.
 - I will notify everyone if I need new command for monitor in case that it is conflict with existing command.
 

 ##additional for monitor information:
 - return F/T: F - 1. not enough quorum 2. operation is not complete
 - every time before write make sure every node is online
 - read not matter write operation is successful.