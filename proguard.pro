-injars       proguard.jar
-outjars      proguard_out.jar
-libraryjars  <java.home>/lib/rt.jar
-printmapping proguard.map

#-keep public class proguard.ProGuard {
#    public static void main(java.lang.String[]);
#}