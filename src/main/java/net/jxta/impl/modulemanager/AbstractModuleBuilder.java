package net.jxta.impl.modulemanager;

import java.util.ArrayList;
import java.util.Collection;

import net.jxta.module.IModuleBuilder;
import net.jxta.module.IModuleBuilderListener;
import net.jxta.module.IModuleBuilderListener.BuildEvents;
import net.jxta.module.IModuleDescriptor;

public abstract class AbstractModuleBuilder<T extends Object> implements IModuleBuilder<T> {

	private Collection<IModuleBuilderListener<T>> listeners; 
	private Collection<IModuleDescriptor> descriptors; 
	private boolean initialised;
	
	public AbstractModuleBuilder() {
		super();
		listeners = new ArrayList<IModuleBuilderListener<T>>();
		descriptors = new ArrayList<IModuleDescriptor>();
	}

	public void activate(){ /* DO NOTHING */};
	public void deactivate(){ /* DO NOTHING */};
	
	/**
	 * Initialise the builder, and return true when the initialisation was succesful
	 * @return
	 */
	protected abstract boolean onInitBuilder( IModuleDescriptor descriptor );
	
	public void initialise( IModuleDescriptor descriptor){
		for( IModuleDescriptor desc: this.descriptors ){
			if(( desc.equals( descriptor )) && ( !desc.isInitialised() ))
				desc.init();
		}
		this.initialised = this.onInitBuilder( descriptor );
		for( IModuleBuilderListener<T> listener: this.listeners )
			listener.notifyModuleBuilt( new ModuleEvent<T>( this, BuildEvents.INITIALSED ));
	}
	
	public boolean isInitialised() {
		return initialised;
	}

	protected void addDescriptor(IModuleDescriptor descriptor) {
		this.descriptors.add(descriptor);
	}

	protected void removeDescriptor(IModuleDescriptor descriptor) {
		this.descriptors.remove(descriptor);
	}

	public void addBuilderListemer(IModuleBuilderListener<T> listener) {
		this.listeners.add(listener);
	}

	public void removeBuilderListemer(IModuleBuilderListener<T> listener) {
		this.listeners.remove(listener);
	}

	public IModuleDescriptor[] getSupportedDescriptors() {
		return descriptors.toArray( new IModuleDescriptor[ this.descriptors.size()]);
	}

	public boolean canBuild(IModuleDescriptor descriptor) {
		for( IModuleDescriptor desc: descriptors )
			if( desc.equals( descriptor ))
				return true;
		return false;
	}

	protected abstract T onBuildModule( IModuleDescriptor descriptor );
	
	public T buildModule(IModuleDescriptor descriptor) throws ModuleException {
		T module = this.onBuildModule( descriptor );
		for( IModuleBuilderListener<T> listener: this.listeners )
			listener.notifyModuleBuilt( new ModuleEvent<T>( this, BuildEvents.INITIALSED ));
		return module;
	}
}
