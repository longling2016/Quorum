# pay attention!

## Let's follow the standard as below:
 - the crash rate is expressed as an int n, which means randomly create a number as rand.nextInt(n), crash rate for this moment is 1/n.
 - please make sure you change the boolean: ifCrash in Info of your class (no phase, two phase, three phase) to true before simulating the crash situation, and change it back to false after node is recovered.
 - Total blocking encountering is calculated by adding all numbers up.
 - Implement an "end()" method to kill the thread you created for listening.
 - Please follow the convention for message parsing:
   - **write<value>**: example as "write10027"
   - **fail**: after writing operation is aborted 
   - **success**: after writing operation is completed successfully.
 
 

 