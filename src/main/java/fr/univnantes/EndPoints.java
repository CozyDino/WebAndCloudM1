package fr.univnantes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultIterable;

import fr.univnantes.PostMessage;

@Api(name = "myApi",
     version = "v1",
     audiences = "414205931351-4fl21dau6999umse2i34jsr4bni0mntv.apps.googleusercontent.com",
  	 clientIds = "414205931351-4fl21dau6999umse2i34jsr4bni0mntv.apps.googleusercontent.com",
  	namespace =
  	@ApiNamespace(
  		   ownerDomain = "helloworld.example.com",
  		   ownerName = "helloworld.example.com",
  		   packagePath = "")
)

public class EndPoints {
	@ApiMethod(name = "postMsg", httpMethod = HttpMethod.POST)
	public Entity postMsg(User user, PostMessage pm) throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}
		
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		
		
		Query qFollowers = new Query("Follow"). //TODO recupérer les receivers en piochant dans la table des follows
	    setFilter(new FilterPredicate("profile", FilterOperator.EQUAL, user.getEmail()));
		PreparedQuery pq = datastore.prepare(qFollowers);
		QueryResultIterable<Entity> results = pq.asQueryResultIterable();

		Entity e = new Entity("Post", Long.MAX_VALUE-(new Date()).getTime()+":"+user.getEmail());
		e.setProperty("owner", user.getEmail());
		e.setProperty("url", pm.url);
		e.setProperty("body", pm.body);
		e.setProperty("likec", 0);
		e.setProperty("date", new Date());

//		Solution pour pas projeter les listes
		
		Entity pi = new Entity("PostIndex", e.getKey());
		HashSet<String> rec=new HashSet<String>();
		
		
		//On ajoute tout les followers dans la liste de réception
		for(Entity entry : results) {
			rec.add(entry.getProperty("follower").toString());
		}
		
		pi.setProperty("receivers",rec);
		
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
		datastore.put(pi);
		txn.commit();
		return e;
	}
	
	//Permet de poster plusieurs fois un message pour tester la scalabilité lorsqu'on va récupérer les posts
	public Entity postMsgTest(User user, PostMessage pm, @Nullable @Named("nbItems") String nbItems) throws UnauthorizedException {

		if (user == null) {
			throw new UnauthorizedException("Invalid credentials");
		}
		
		int iteration = 1;
		
		if(nbItems != null)
			iteration = Integer.parseInt(nbItems);
		
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		
		
		Query qFollowers = new Query("Follow"). //TODO recupérer les receivers en piochant dans la table des follows
	    setFilter(new FilterPredicate("profile", FilterOperator.EQUAL, user.getEmail()));
		PreparedQuery pq = datastore.prepare(qFollowers);
		QueryResultIterable<Entity> results = pq.asQueryResultIterable();

		

//		Solution pour pas projeter les listes
		
		
		//on supprime tout les posts
		Query qPost = new Query("Post").setKeysOnly();
		
		PreparedQuery pqPost = datastore.prepare(qPost);
		
		QueryResultList<Entity> list = pqPost.asQueryResultList(FetchOptions.Builder.withDefaults());
		
		Query qPostIndex = new Query("PostIndex").setKeysOnly();
		
		PreparedQuery pqPostIndex = datastore.prepare(qPost);
		
		QueryResultList<Entity> listIndex = pqPost.asQueryResultList(FetchOptions.Builder.withDefaults());
		
		Transaction txn = datastore.beginTransaction();
		
		//On supprime tout les posts précédents
		for(Entity post : list) {
			datastore.delete(post.getKey());
		}
		
		for(Entity postIndex : listIndex) {
			datastore.delete(postIndex.getKey());
		}
		
		
		for(int i = 0 ; i < iteration; i++) {	
			Entity e = new Entity("Post", Long.MAX_VALUE-(new Date()).getTime()+":"+user.getEmail());
			e.setProperty("owner", user.getEmail());
			e.setProperty("url", pm.url+i);
			e.setProperty("body", pm.body);
			e.setProperty("likec", 0);
			e.setProperty("date", new Date());
			
			Entity pi = new Entity("PostIndex", e.getKey());
			HashSet<String> rec=new HashSet<String>();
			
			
			//On ajoute tout les followers dans la liste de réception
			for(Entity entry : results) {
				rec.add(entry.getProperty("follower").toString());
			}
			
			pi.setProperty("receivers",rec);
			
			
			datastore.put(e);
			datastore.put(pi);
		}
		txn.commit();
		return null;
	}
	
	//TODO récuperer liste de message pour l'utilisateur et pour ses amis, via receiver list ?
	@ApiMethod(name = "getPost",
			   httpMethod = ApiMethod.HttpMethod.GET)
		public CollectionResponse<Entity> getPost(User user, @Nullable @Named("next") String cursorString, @Nullable @Named("nbItems") String nbItems)
				throws UnauthorizedException {

			int nombrePost = 2;
		
			if (user == null) {
				throw new UnauthorizedException("Invalid credentials");
			}
			
			if (nbItems != null) {
				try {					
					nombrePost = Integer.parseInt(nbItems);
				} catch (NumberFormatException e) {
					nombrePost = 2;
				}
			}

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

			Query qIndex = new Query("PostIndex").setFilter(new FilterPredicate("receivers", FilterOperator.EQUAL, user.getEmail()));
			qIndex.setKeysOnly();
			
			PreparedQuery pqIndex = datastore.prepare(qIndex);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(nombrePost);
			
			if (cursorString != null) {
				fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
			}
			
			QueryResultList<Entity> resultIndex = pqIndex.asQueryResultList(fetchOptions);
			
		
			ArrayList<Key> keys = new ArrayList<Key>();
			
			for(Entity entity : resultIndex) {
				keys.add(entity.getParent());
			}
			
			Map<Key, Entity> msgs = datastore.get(keys);
			
			Collection<Entity> coll = new LinkedList<Entity>();
			
			for(Entity e : msgs.values()) {
				coll.add(e);
			}

			
			cursorString = resultIndex.getCursor().toWebSafeString();

			return CollectionResponse.<Entity>builder().setItems(coll).setNextPageToken(cursorString).build();
		}
	
	
	//TODO
	@ApiMethod(name="follow", httpMethod=HttpMethod.POST)
	public Entity follow(User user, @Named("email") String profileEmail) throws UnauthorizedException {
		if(user==null)
			throw new UnauthorizedException("invalid credentials");
		
		Entity e = new Entity("Follow",""+user.getEmail()+profileEmail);
		e.setProperty("follower", user.getEmail());
		e.setProperty("profile", profileEmail);
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
		txn.commit();
		return e;
	}
	
	@ApiMethod(name="followTest", httpMethod=HttpMethod.POST)
	public Entity followTest(User user, @Named("numberFriend") String numberFriend) throws UnauthorizedException {
		if(user==null)
			throw new UnauthorizedException("invalid credentials");
		
		
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query qFriend = new Query("Follow").setKeysOnly();
		
		PreparedQuery pq = datastore.prepare(qFriend);
		
		QueryResultList<Entity> list = pq.asQueryResultList(FetchOptions.Builder.withDefaults());
		
		Transaction txn = datastore.beginTransaction();
		
		for(Entity e : list) {
			datastore.delete(e.getKey());
		
		}
		
		for(int i = 0; i < Integer.parseInt(numberFriend); i++) {
			Entity e = new Entity("Follow",""+"test"+i+"@gmail.com"+user.getEmail());
			e.setProperty("profile", user.getEmail());
			e.setProperty("follower", "test"+i+"@gmail.com");
			datastore.put(e);
		}
		
		txn.commit();
		
		return null;
	}
	
	
}
