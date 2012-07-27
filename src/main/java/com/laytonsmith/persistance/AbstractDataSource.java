package com.laytonsmith.persistance;

import com.laytonsmith.PureUtilities.StringUtils;
import com.laytonsmith.annotations.datasource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 
 * @author lsmith
 */
public abstract class AbstractDataSource implements DataSource{

    protected final URI uri;
    protected final Set<DataSourceModifier> modifiers = EnumSet.noneOf(DataSourceModifier.class);    
    private Set<DataSourceModifier> invalidModifiers;
    
    protected AbstractDataSource(URI uri) throws DataSourceException{
        this.uri = uri;
        setInvalidModifiers();
        DataSourceModifier [] implicit = this.implicitModifiers();
        if(implicit != null){
            for(DataSourceModifier dsm : this.implicitModifiers()){
                addModifier(dsm);
            }
        }
        populate();
    }        
        

    /**
     * The default implementation of string simply walks through keySet, and
     * manually joins the keys together. If an implementation can provide a more
     * efficient method, this should be overridden.
     * @return 
     */
    public List<String> stringKeySet() {
        List<String> keys = new ArrayList<String>();
        for(String [] key : keySet()){
            keys.add(StringUtils.Join(key, "."));
        }
        return keys;
    }

    public List<String[]> getNamespace(String[] namespace) throws DataSourceException {
        List<String[]> list = new ArrayList<String[]>();
        String ns = StringUtils.Join(namespace, ".");
        for(String key : stringKeySet()){
            if(key.startsWith(ns)){
                String[] split = key.split("\\.");
                list.add(split);
            }
        }
        return list;
    }
    
    private void setInvalidModifiers(){
        DataSourceModifier[] invalid = this.invalidModifiers();
        if(invalid == null){
            return;
        }
        this.invalidModifiers = EnumSet.copyOf(Arrays.asList(invalid));
    }
    
    public final String getName() {
        return this.getClass().getAnnotation(datasource.class).value();
    }

    public final void addModifier(DataSourceModifier modifier) {        
        if(invalidModifiers != null && invalidModifiers.contains(modifier)){
            return;
        }
        if(modifier == DataSourceModifier.HTTP || modifier == DataSourceModifier.HTTPS){
            modifiers.add(DataSourceModifier.READONLY);
            modifiers.add(DataSourceModifier.ASYNC);
        }
        modifiers.add(modifier);
    }
    
    /**
     * This method checks for invalid or non-sensical combinations of modifiers, and throws
     * an exception if any combinations exist that are strange.
     * @throws DataSourceException 
     */
    public final void checkModifiers() throws DataSourceException{
        List<String> errors = new ArrayList();
        if(invalidModifiers != null){
            for(DataSourceModifier dsm : invalidModifiers){
                if(modifiers.contains(dsm)){
                    errors.add(uri.toString() + " contains the modifier " + dsm.getName() + ", which is not applicable. This will be ignored.");
                }
            }
        }
        if(modifiers.contains(DataSourceModifier.PRETTYPRINT) && modifiers.contains(DataSourceModifier.READONLY)){
            errors.add(uri.toString() + " contains both prettyprint and readonly modifiers, which doesn't make sense, because we cannot write out the file; prettyprint will be ignored.");
            modifiers.remove(DataSourceModifier.PRETTYPRINT);
        }
        if(modifiers.contains(DataSourceModifier.HTTP) && modifiers.contains(DataSourceModifier.HTTPS)){
            errors.add(uri.toString() + " contains both http and https modifiers. Because these are mutually exclusive, this doesn't make sense, and https will be assumed.");
            modifiers.remove(DataSourceModifier.HTTP);
        }
        if(!errors.isEmpty()){
            throw new DataSourceException(StringUtils.Join(errors, "\n"));
        }
    }
    
    public final boolean hasModifier(DataSourceModifier modifier){
        return modifiers.contains(modifier);
    }

    /**
     * This method checks to see if a set operation should simply throw
     * a ReadOnlyException based on the modifiers.
     */
    protected final void checkSet() throws ReadOnlyException {
        if(modifiers.contains(DataSourceModifier.READONLY)){
            throw new ReadOnlyException();
        }
    }
    
    /**
     * This method checks to see if get operations should re-populate at this
     * time. If the data set is transient, it will do so.
     */
    protected final void checkGet() throws DataSourceException {
        if(hasModifier(DataSourceModifier.TRANSIENT)){
            populate();
        }
    }

    public final List<DataSourceModifier> getModifiers() {
        return new ArrayList<DataSourceModifier>(modifiers);
    }       
    
    /**
     * Given a URI, extracts the file path from it. Unlike URI.getPath(), this
     * will actually pull the appropriate information from the URI, regardless of if
     * this is relative or absolute.
     * @param uri
     * @return 
     */
    protected static String GetFilePath(URI uri){
        String file = (uri.getHost() == null?"":uri.getHost()) + uri.getPath();
        return file;
    }
}
