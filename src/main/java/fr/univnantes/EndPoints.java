package fr.univnantes;

import java.util.Date;
import java.util.HashSet;

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
			rec.add(e.getProperty("follower").toString());
		}
		
		pi.setProperty("receivers",rec);
		
		Transaction txn = datastore.beginTransaction();
		datastore.put(e);
		datastore.put(pi);
		txn.commit();
		return e;
	}
	
	//TODO récuperer liste de message pour l'utilisateur et pour ses amis, via receiver list ?
	@ApiMethod(name = "getPost",
			   httpMethod = ApiMethod.HttpMethod.GET)
		public CollectionResponse<Entity> getPost(User user, @Nullable @Named("next") String cursorString)
				throws UnauthorizedException {

			if (user == null) {
				throw new UnauthorizedException("Invalid credentials");
			}

			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

			
			Query q = new Query("Post").
			    setFilter(new FilterPredicate("owner", FilterOperator.EQUAL, user.getEmail()));
			PreparedQuery pq = datastore.prepare(q);

			
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(2);

			if (cursorString != null) {
				fetchOptions.startCursor(Cursor.fromWebSafeString(cursorString));
			}

			QueryResultList<Entity> results = pq.asQueryResultList(fetchOptions);
			cursorString = results.getCursor().toWebSafeString();

			return CollectionResponse.<Entity>builder().setItems(results).setNextPageToken(cursorString).build();
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
}
