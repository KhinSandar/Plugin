package Workers;

import Models.GearSpec.GearSpec;
import Models.GearSpec.GearSpecDependency;
import Models.GearSpec.GearSpecSource;
import Utilities.GearSpecRegistrar;
import Utilities.Utils;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.FileTypeUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by matthewyork on 4/4/14.
 */
public class InstallDependencyForSpecWorker extends SwingWorker<Void, Void> {

    private GearSpec selectedSpec;
    private Project project;
    private Module module;
    public boolean successful;

    public InstallDependencyForSpecWorker(GearSpec spec, Project project, Module module) {
        this.selectedSpec = spec;
        this.project = project;
        this.module = module;
    }

    @Override
    protected Void doInBackground() throws Exception {

        if (selectedSpec != null){
            if (selectedSpec.getType().equals(GearSpec.SPEC_TYPE_JAR)){
                if (installJar(this.selectedSpec)){
                    successful = true;
                    return null;
                }
            }
            else if (selectedSpec.getType().equals(GearSpec.SPEC_TYPE_MODULE)){
                if (installModule(this.selectedSpec)){
                    successful = true;
                    return null;
                }
            }


        }

        successful = false;
        return null;
    }


    private Boolean installModule(GearSpec spec){
        //Install dependency and sub-dependencies
        File specDirectory = new File(project.getBasePath() + Utils.pathSeparator() + "Gears" + Utils.pathSeparator() + "Modules" + Utils.pathSeparator() + spec.getName());

        //Delete the directory. This is for other versions installed
        if (specDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(specDirectory);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        //Clone down repo
        try {
            Git.cloneRepository()
                    .setURI(spec.getSource().getUrl())
                    .setBranch(spec.getSource().getTag())
                    .setDirectory(specDirectory)
                    .call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return false;
        }

        //Check out appropriate branch
        File gitDirectory = new File(specDirectory.getAbsolutePath() + Utils.pathSeparator() + ".git");
        try {
            Git git = Git.open(gitDirectory);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        //Move specified folder to root, if paremeter exists
        if (spec.getSource().getSource_files() != null){
            if (!spec.getSource().getSource_files().equals("")){
                //Replaces path seperators with system dependent ones (windows, mac, etc.)
                String systemSpecificInnerPath = spec.getSource().getSource_files().replace("/", Utils.pathSeparator());

                //Get module directory
                File moduleDirectory = new File(specDirectory.getAbsolutePath()+Utils.pathSeparator()+systemSpecificInnerPath);

                //Delete other folders, including source control
                for(File file : specDirectory.listFiles()){
                    //Delete all folders that aren't the lib folder
                    if (!file.getAbsolutePath().equals(moduleDirectory.getAbsolutePath())){
                        try {
                            if (file.isDirectory()){
                                FileUtils.deleteDirectory(file);
                            }
                            else {
                                FileUtils.forceDelete(file);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                }

                //Move module to root
                for(File file : moduleDirectory.listFiles()){
                    try {
                        if (file.isDirectory()){
                            FileUtils.copyDirectoryToDirectory(file, specDirectory);

                        }
                        else {
                            FileUtils.copyFileToDirectory(file, specDirectory);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                //Finally, delete old inner module folder
                try {
                    FileUtils.deleteDirectory(moduleDirectory);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }

        //Download dependencies
        if (spec.getDependencies() != null){
            if (spec.getDependencies().size() > 0){
                for(GearSpecDependency dependency : spec.getDependencies()){
                    //Get spec from dependency
                    GearSpec dependencySpec = Utils.specForInfo(dependency.getName(), dependency.getVersion());

                    //If we get a valid spec from the dependency, go ahead and download the dependency
                    if (dependencySpec != null){
                        //See if it is installed already, before we try
                        if (!dependencySpec.isInstalled(project)){

                            //Install dependency
                            if (dependencySpec.getType().equals(GearSpec.SPEC_TYPE_JAR)){
                                installJar(dependencySpec);
                            }
                            else if (dependencySpec.getType().equals(GearSpec.SPEC_TYPE_MODULE)){
                                installModule(dependencySpec);
                            }
                        }
                    }
                }
            }
        }

        //Update project settings
        if (!updateProjectSettingsForModule()){
            return false;
        }

        //Register spec
        if (GearSpecRegistrar.registerGear(spec, project)){
            return true;
        }
        else {
            return false;
        }
    }

    private Boolean installJar(GearSpec spec){
        //Create GearsJars directory if not already there
        File libsDirectory = new File(project.getBasePath()+Utils.pathSeparator()+ "Gears"+ Utils.pathSeparator() + "Jars");
        if (!libsDirectory.exists()){
            try {
                FileUtils.forceMkdir(libsDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Build jar file
        File jarFile = new File(libsDirectory.getAbsolutePath()+Utils.pathSeparator()+Utils.jarFileNameForSpecSource(spec.getSource()));

        //Build url for gear
        String jarUrl = spec.getSource().getUrl()+"/raw/"+spec.getSource().getTag()+"/"+spec.getSource().getSource_files();
        jarUrl = jarUrl.replace(".git", "");

        //Download file
        try {
            FileUtils.copyURLToFile(new URL(jarUrl), jarFile, 2000, 30000);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        //Download dependencies
        if (spec.getDependencies() != null){
            if (spec.getDependencies().size() > 0){
                for(GearSpecDependency dependency : spec.getDependencies()){
                    //Get spec from dependency
                    GearSpec dependencySpec = Utils.specForInfo(dependency.getName(), dependency.getVersion());

                    //If we get a valid spec from the dependency, go ahead and download the dependency
                    if (dependencySpec != null){
                        //See if it is installed already, before we try
                        if (!dependencySpec.isInstalled(project)){

                            //Install dependency
                            if (dependencySpec.getType().equals(GearSpec.SPEC_TYPE_JAR)){
                                installJar(dependencySpec);
                            }
                            else if (dependencySpec.getType().equals(GearSpec.SPEC_TYPE_MODULE)){
                                installModule(dependencySpec);
                            }
                        }
                    }
                }
            }
        }

        //Register spec
        if (GearSpecRegistrar.registerGear(spec, project)){
            return true;
        }
        else {
            return false;
        }
    }

    private Boolean updateProjectSettingsForModule(){

        //Install dependency and sub-dependencies
        File settingsFile = new File(project.getBasePath() + Utils.pathSeparator() + "settings.gradle");
        File buildFile = new File(new File(module.getModuleFilePath()).getParentFile().getAbsolutePath() + Utils.pathSeparator() + "build.gradle");

        //Create comment string
        String commentString = "\n/////////////////////\n" +
                "// Gears Dependencies\n" +
                "/////////////////////";

        //Read settings file
        try {
            String settingsFileString = FileUtils.readFileToString(settingsFile);

            if (!settingsFileString.contains("include ':Gears:Modules:"+this.selectedSpec.getName()+"'")){

                //Make changes to settings.gradle
                String newSettingString = "\n"+"include ':Gears:Modules:"+this.selectedSpec.getName()+"'";

                int commentIndex = settingsFileString.lastIndexOf(commentString);

                //If the comment exists...
                if (commentIndex != -1){
                    settingsFileString = settingsFileString.concat(newSettingString);
                }
                else {
                    settingsFileString = settingsFileString.concat(commentString+"\n"+newSettingString);
                }

                //Write changes to settings.gradle
                FileUtils.forceDelete(settingsFile);
                FileUtils.write(settingsFile, settingsFileString);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        //Read build file
        try {
            String buildFileString = FileUtils.readFileToString(buildFile);

            //Create new addition
            String newDependencyString = "dependencies{compile project (':Gears:Modules:"+this.selectedSpec.getName()+"')}";


            if (!buildFileString.contains(newDependencyString)){
                int commentIndex = buildFileString.lastIndexOf(commentString);

                //If the comment exists...
                if (commentIndex != -1){
                    buildFileString = buildFileString.concat(newDependencyString);
                }
                else {
                    buildFileString = buildFileString.concat(commentString+"\n"+newDependencyString);
                }

                //Write changes to settings.gradle
                FileUtils.forceDelete(buildFile);
                FileUtils.write(buildFile, buildFileString);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private Boolean updateProjectSettingsForJar(){
        return false;
    }
}
