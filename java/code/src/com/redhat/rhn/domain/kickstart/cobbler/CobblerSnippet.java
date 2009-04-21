/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.redhat.rhn.domain.kickstart.cobbler;

import com.redhat.rhn.common.conf.Config;
import com.redhat.rhn.common.util.FileUtils;
import com.redhat.rhn.common.validator.ValidatorException;
import com.redhat.rhn.domain.org.Org;

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CobblerSnippet - Class representation of a Cobbler snippet
 * @version $Rev: 1 $
 */
public class CobblerSnippet implements Comparable<CobblerSnippet> {
    private File path;
    private Org org;
    
    
    /**
     * Method to return the main cobbler snippets dir
     * (i.e. /var/lib/cobbler/snippets) .. Reason this is
     * a method is because we want it to be
     * changeable for unit tests..
     * @return the cobbler snippets dir
     */
    public static File getCobblerSnippetsDir() {
        File fl = new File(Config.get().getCobblerSnippetsDir());
        return fl;
    }

    /**
     * Method to return the base spacewalk snippets dir
     * (i.e. /var/lib/cobbler/snippets/spacewalk) .. 
     * Reason this is
     * a method is because we want it to be
     * changeable for unit tests..
     * @return the spacewalk snippets dir
     */
    public static File getSpacewalkSnippetsDir() {
        return new File(getCobblerSnippetsDir(), "spacewalk");
    }
    
    /**
     * Cobbler Snippet method to be called
     * when creating or updating the actual instance of a cobbler snippet.
     * This is in a typical case would be considered a 
     * manager layer method but its small enough to merit not creating 
     * another command/class for it.
     * @param create True if we are creating a new editable snippet
     *               False updating an existing editable snippet
     * @param name the name of the snippet. 
     * @param contents the contents of the snippet.
     * @param org the org of the editable snippet.
     * @return the newly Created or updated cobbler snippet..
     */
    public static final CobblerSnippet createOrUpdate(boolean create, 
                                    String name, String contents, Org org) {
        CobblerSnippet snip = loadEditable(name, org);
        
        if (create && snip.path.exists()) {
            ValidatorException.raiseException("cobbler.snippet.filenameexists.message");
        }
        snip.writeContents(contents);
        return snip;
    }    

    private CobblerSnippet() {
    }
    
    /**
     * Constructor to load a spacewalk Editable (as in Org Based) cobbler snippet..
     * These are snippets that reside in 
     *      /var/lib/cobbler/snippets/spacewalk/${org.id}/....
     * Note validation errors will be raised if the name contains slashes
     *  /var/lib/cobbler/snippets/spacewalk/${org.id}/${name}
     * @param nameIn the snippet name ${name} in 
     *          ${spacewalk.snippets.dir}/${org.id}/${name}
     * @param orgIn the org in ${spacewalk.snippets.dir}/${org.id}/${name}
     * @return the cobbler snippet.
     */
    public static CobblerSnippet loadEditable(String nameIn, Org orgIn) {
        validateFileName(nameIn);
        CobblerSnippet snippy = new CobblerSnippet();
        snippy.org = orgIn;
        snippy.path = new File(getPrefixFor(snippy.org) + "/" + nameIn);
        return snippy;
    }
    /**
     * Constructor load a non editable spacewalk cobbler snippets
     * as in all the snippets that reside under 
     *  /var/lib/cobbler/snippets/ except
     *  /var/lib/cobbler/snippets/spacewalk
     *  Idea here is that this list is read only
     *  and operations such as write operations cannot be performed..
     * @param pathIn /var/lib/cobbler/snippets/foo/bar
     * @return the cobbler snippet.
     */
    public static CobblerSnippet loadReadOnly(File pathIn) {
        validateCommonPath(pathIn);
        CobblerSnippet snippy = new CobblerSnippet();
        snippy.path = pathIn;
        return snippy;
    }    
    
    /**
     * The path for display purposes
     * @return the display path
     */
    public String getDisplayPath() {
        return getPath().getAbsolutePath();
    }
    
    /** 
     * Getter for name 
     * @return String to get
    */
    public File getPath() {
        return this.path;
    }

    /** 
     * Getter for contents 
     * @return String to get
    */
    public String getContents() {
        if (path.exists()) {
            return FileUtils.readStringFromFile(path.getAbsolutePath());
        }
        return null;
    }
    
    /**
     * Basically writes the snippet contents sent to this method
     * to the disk..
     * Note: only editable snippets can be updated,
     *  i.e. snippets under
     * ${spacewalk.snippets.dir}/${org.id}/${name}
     * @param contents the contents of the snippet
     */
    public void writeContents(String contents) {
        verifyEditable();
        if (!path.exists()) {
            path.getParentFile().mkdirs();
        }
        FileUtils.writeStringToFile(contents, path.getAbsolutePath());
    }

    
    /**
     * Method to allow you to delete the snippet. 
     * Note: only editable snippets can be deleted, i.e. snippets under
     * ${spacewalk.snippets.dir}/${org.id}/${name}
     */
    public void delete() {
        verifyEditable();
        if (!path.exists() || !path.delete()) {
            ValidatorException.raiseException("cobbler.snippet.couldnotdelete.message",
                                                                    getDisplayPath());
        }
    }
    
    /**
     * Note: only snippets under
     * ${spacewalk.snippets.dir}/${org.id}/${name}
     * are editable..
     * @return true if this cobbler snippet is editable.. 
     */
    public boolean isEditable() {
        return !isCommonPath(path);
    }
    
    
    /**
     * Returns just the name of the snippet file (same as basename)
     * i.e. returns ${name} in ${spacewalk.snippets.dir}/${org.id}/${name} 
     * @return the base name of the snippet file  
     */
    public String getName() {
        return path.getName();
    }
    
    /**
     * Returns the name of the directory hosting the snippet file (same as dirname)
     * i.e. returns ${spacewalk.snippets.dir}/${org.id}
     *  in ${spacewalk.snippets.dir}/${org.id}/${name} 
     * @return the name of the directory hosting the snippet file
     */
    public String getPrefix() {
        return path.getParent();
    }

    /**
     * Returns the name of the dir that should be hosting scripts
     * for the snippet. This is useful for example while
     * creating snippets. 
     * @param org  the org hosting the snippet, or null if its a common org 
     * @return the name of the directory that should host the snippet file
     */
    public static String getPrefixFor(Org org) {
        if (org == null) {
            return getCobblerSnippetsDir().getAbsolutePath();
        }
        return getSpacewalkSnippetsDir().getAbsolutePath() + "/" + org.getId();
    }

    private static void validateFileName(String name) {
        // only [a-zA-Z_0-9] and '.' and '-' are valid filename characters
        Pattern p = Pattern.compile("^[\\w\\.\\-_]+$");
        Matcher m = p.matcher(name);
        if (!m.matches()) {
            ValidatorException.raiseException("cobbler.snippet.invalidfilename.message");
        }
    }
    
    private static void validateCommonPath(File path) {
        if (!path.exists() || path.isHidden() || !path.isFile() || 
                              !isCommonPath(path)) {
            ValidatorException.raiseException("cobbler.snippet.invalidfilename.message");
        }
    }
    
    private static boolean isCommonPath(File path) {
        return !path.getAbsolutePath().startsWith(
                        getSpacewalkSnippetsDir().getAbsolutePath()) && 
                    path.getAbsolutePath().
                            startsWith(getCobblerSnippetsDir().getAbsolutePath()); 
    }
    
    private void verifyEditable() {
        if (!isEditable()) {
            ValidatorException.raiseException("cobbler.snippet.invalidfilename.message");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(CobblerSnippet o) {
        if (equals(o)) {
            return 0;
        }
        return this.path.compareTo(o.path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CobblerSnippet)) {
            return false;
        }
        
        CobblerSnippet that = (CobblerSnippet) o;
        return getPath().equals(that.getPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(getPath());
        return b.toHashCode();
    }    
}
